package com.rufana.instantlore.server.task;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.LoreReportBus;
import com.rufana.instantlore.server.PlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskRunner {
	private static final double MAX_TOTAL_SECONDS = 600.0;

	private static final Map<UUID, Running> TASKS = new ConcurrentHashMap<>();

	private TaskRunner() {
	}

	private static final class Running {
		final Deque<TaskStep> steps;
		final long startedAt = System.nanoTime();
		TaskStep current;
		long stepStartedAt;
		int index;
		final int total;

		Running(List<TaskStep> steps) {
			this.steps = new ArrayDeque<>(steps);
			this.total = steps.size();
		}
	}

	public static void register() {
		LoreReportBus.subscribe(TaskRunner::onReport);
		ServerTickEvents.END_SERVER_TICK.register(TaskRunner::onTick);
	}

	public static void start(ServerPlayerEntity player, List<TaskStep> steps) {
		if (steps.isEmpty()) {
			player.sendMessage(Text.literal("Задача пустая"), false);
			return;
		}

		cancel(player, "заменена новой");

		Running running = new Running(steps);
		TASKS.put(player.getUuid(), running);

		LoreDevLog.info("Task for {}: steps {}",
				player.getName().getString(), steps.size());

		advance(player, running);
	}

	public static void cancel(ServerPlayerEntity player, String why) {
		if (TASKS.remove(player.getUuid()) != null) {
			PlayerEvents.release(player);
			LoreDevLog.info("Task for {} cancelled: {}",
					player.getName().getString(), why);
		}
	}

	private static void advance(ServerPlayerEntity player, Running running) {
		TaskStep next = running.steps.poll();

		if (next == null) {
			finish(player, running, true, "все шаги выполнены");
			return;
		}

		running.current = next;
		running.stepStartedAt = System.nanoTime();
		running.index++;

		LoreDevLog.info("[{}] step {}/{}: {}",
				player.getName().getString(), running.index, running.total, next.describe());

		try {
			next.start(player);
		} catch (TaskFailure failure) {
			finish(player, running, false, failure.getMessage());
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Step '{}' failed", next.describe(), e);
			finish(player, running, false, "step failed with an error");
		}
	}

	private static void onTick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Running> entry : TASKS.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

			if (player == null) {
				TASKS.remove(entry.getKey());
				continue;
			}

			tickOne(player, entry.getValue());
		}
	}

	private static void tickOne(ServerPlayerEntity player, Running running) {
		long now = System.nanoTime();

		if (now - running.startedAt > MAX_TOTAL_SECONDS * 1_000_000_000L) {
			finish(player, running, false, "задача идёт слишком долго");
			return;
		}

		TaskStep step = running.current;

		if (step == null) {
			return;
		}

		boolean expired = now - running.stepStartedAt
				> step.timeoutSeconds() * 1_000_000_000L;

		try {
			if (step.onTick(player)) {
				advance(player, running);
				return;
			}
		} catch (TaskFailure failure) {
			finish(player, running, false, failure.getMessage());
			return;
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Step '{}' tick failed", step.describe(), e);
			finish(player, running, false, "step failed with an error");
			return;
		}

		if (!expired) {
			return;
		}

		if (step.failsOnTimeout()) {
			finish(player, running, false, "шаг не уложился в срок: " + step.describe());
		} else {
			advance(player, running);
		}
	}

	private static void onReport(ServerPlayerEntity player, String type, JsonObject data) {
		Running running = TASKS.get(player.getUuid());

		if (running == null || running.current == null) {
			return;
		}

		try {
			if (running.current.onReport(player, type, data)) {
				advance(player, running);
			}
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Step report handling failed", e);
			finish(player, running, false, "step failed with an error");
		}
	}

	private static void finish(ServerPlayerEntity player, Running running,
			boolean ok, String why) {
		TASKS.remove(player.getUuid());
		PlayerEvents.release(player);

		String text = ok
				? "Задача выполнена: " + why
				: "Задача прервана на шаге " + running.index + "/" + running.total + " — " + why;

		LoreDevLog.info("[{}] {}", player.getName().getString(), text);
		player.sendMessage(
				Text.literal(text).formatted(ok ? Formatting.GREEN : Formatting.RED), false);
	}
}
