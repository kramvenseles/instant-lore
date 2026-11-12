package com.rufana.instantlore.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RitualRunner {
	private static final int TPS = 20;

	private static final int HARD_TIMEOUT_TICKS = 15 * TPS;

	private static final int BLOOD = 0x8A0303;

	private static final Map<UUID, Running> ACTIVE = new ConcurrentHashMap<>();

	private RitualRunner() {
	}

	private record Beat(int tick, String label, Action action) {
	}

	@FunctionalInterface
	private interface Action {
		void run(ServerPlayerEntity player, ServerWorld world);
	}

	private static final List<Beat> BEATS = List.of(
			new Beat(0, "камера-перед, захват", (p, w) -> {
				PlayerEvents.control(p, true, true, 13);
				PlayerEvents.blockAllKeysExcept(p, List.of(), 13);
				PlayerEvents.send(p, "camera.perspective", "{\"mode\":\"third_front\"}");
				PlayerEvents.send(p, "camera.distance", "{\"blocks\":7}");
			}),
			new Beat(6, "сгорбиться, взгляд вниз", (p, w) -> {
				PlayerEvents.send(p, "player.input", "{\"sneak\":true,\"seconds\":12}");
				PlayerEvents.send(p, "player.look", "{\"pitch\":22}");
				shake(p, 0.12, 5, 4.5);
			}),
			new Beat(30, "первый удар", (p, w) -> {
				PlayerEvents.click(p, "left", 1);
				blood(p, w, 12, 0.9f);
				shake(p, 0.35, 12, 0.4);
				hurt(p, 0.9);
			}),
			new Beat(60, "второй удар", (p, w) -> {
				PlayerEvents.click(p, "left", 1);
				blood(p, w, 18, 1.0f);
				shake(p, 0.5, 14, 0.4);
				hurt(p, 0.75);
			}),
			new Beat(90, "красная вспышка", (p, w) ->
					PlayerEvents.send(p, "screen.fade",
							"{\"color\":\"#55AA0000\",\"in\":0.2,\"hold\":0.4,\"out\":0.6}")),
			new Beat(110, "роковой удар", (p, w) -> {
				PlayerEvents.click(p, "left", 1);
				blood(p, w, 40, 1.3f);
				shake(p, 0.8, 10, 0.6);
				PlayerEvents.send(p, "camera.fov", "{\"multiplier\":0.75,\"seconds\":1.4}");
				hurt(p, 0.6);
			}),
			new Beat(130, "затемнение", (p, w) ->
					PlayerEvents.send(p, "screen.fade",
							"{\"color\":\"#EE1A0000\",\"in\":1.5,\"hold\":3.0,\"out\":1.5}")),
			new Beat(170, "ритуальная смерть", (p, w) -> {
				blood(p, w, 60, 1.5f);
				PlayerEvents.send(p, "player.uninput", "{}");
				PlayerEvents.release(p);
				PlayerEvents.clearKeys(p);
				PlayerEvents.send(p, "camera.distance", "{\"clear\":true}");
				PlayerEvents.send(p, "camera.perspective", "{\"mode\":\"first\"}");
				LimboTransfer.beginRitualDeath(p, w);
			})
	);

	private static final class Running {
		int tick;
		int nextBeat;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RitualRunner::onTick);
	}

	public static boolean isRunning(ServerPlayerEntity player) {
		return ACTIVE.containsKey(player.getUuid());
	}

	public static boolean start(ServerPlayerEntity player) {
		if (ACTIVE.putIfAbsent(player.getUuid(), new Running()) != null) {
			return false;
		}

		LoreDevLog.info("Ritual started: {}", player.getName().getString());
		return true;
	}

	public static void cancel(ServerPlayerEntity player, String why) {
		if (ACTIVE.remove(player.getUuid()) != null) {
			PlayerEvents.send(player, "player.uninput", "{}");
			PlayerEvents.release(player);
			PlayerEvents.clearKeys(player);
			PlayerEvents.send(player, "camera.distance", "{\"clear\":true}");
			PlayerEvents.send(player, "camera.perspective", "{\"mode\":\"first\"}");
			PlayerEvents.send(player, "screen.fade", "{\"clear\":true}");
			LoreDevLog.info("Ritual aborted at {}: {}", player.getName().getString(), why);
		}
	}

	private static void onTick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Running> entry : ACTIVE.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

			if (player == null) {
				ACTIVE.remove(entry.getKey());
				continue;
			}

			tickOne(player, entry.getValue());
		}
	}

	private static void tickOne(ServerPlayerEntity player, Running running) {
		if (running.tick > HARD_TIMEOUT_TICKS) {
			cancel(player, "сцена не уложилась в потолок");
			return;
		}

		while (running.nextBeat < BEATS.size()
				&& BEATS.get(running.nextBeat).tick() <= running.tick) {
			Beat beat = BEATS.get(running.nextBeat++);
			playBeat(player, beat);
		}

		if (running.nextBeat >= BEATS.size()) {
			ACTIVE.remove(player.getUuid());
			LoreDevLog.info("Ritual completed: {}", player.getName().getString());
			return;
		}

		running.tick++;
	}

	private static void playBeat(ServerPlayerEntity player, Beat beat) {
		LoreDevLog.info("[ritual {}] {}", player.getName().getString(), beat.label());

		if (!(player.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}

		try {
			beat.action().run(player, world);
		} catch (Throwable e) {
			com.rufana.instantlore.InstantLore.LOGGER.error(
					"Ritual beat '{}' failed at {}", beat.label(), player.getName().getString(), e);
		}
	}

	private static void blood(ServerPlayerEntity player, ServerWorld world, int count, float scale) {
		world.spawnParticles(new DustParticleEffect(BLOOD, scale),
				player.getX(), player.getBodyY(0.6), player.getZ(),
				count, 0.25, 0.35, 0.25, 0.02);
	}

	private static void shake(ServerPlayerEntity player, double amplitude, double frequency,
			double seconds) {
		PlayerEvents.send(player, "camera.shake",
				"{\"amplitude\":" + amplitude + ",\"frequency\":" + frequency
						+ ",\"seconds\":" + seconds + "}");
	}

	private static void hurt(ServerPlayerEntity player, double pitch) {
		PlayerEvents.send(player, "sound.play",
				"{\"id\":\"minecraft:entity.player.hurt\",\"category\":\"player\""
						+ ",\"x\":" + player.getX() + ",\"y\":" + player.getBodyY(0.6)
						+ ",\"z\":" + player.getZ() + ",\"volume\":1.0,\"pitch\":" + pitch + "}");
	}
}
