package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeRunner {
	private static final double SESSION_SECONDS = 30.0;

	private static final int REARM_INTERVAL_TICKS = 200;

	public record Options(boolean head, boolean body, boolean keys, boolean chat) {
		public static final Options DEFAULT = new Options(true, true, true, false);

		java.util.List<String> allowed() {
			return chat ? java.util.List.of() : java.util.List.of("key.chat", "key.command");
		}

		public String describe() {
			StringBuilder out = new StringBuilder();
			out.append(head ? "голова" : "-голова").append(", ");
			out.append(body ? "тело" : "-тело").append(", ");
			out.append(keys ? "кнопки" : "-кнопки").append(", ");
			out.append(chat ? "чат" : "-чат");
			return out.toString();
		}
	}

	private static final Map<UUID, Held> frozen = new ConcurrentHashMap<>();

	private record Held(String name, Options options) {
	}

	private static int ticks;

	private FreezeRunner() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (frozen.isEmpty()) {
				return;
			}

			if (++ticks < REARM_INTERVAL_TICKS) {
				return;
			}

			ticks = 0;

			for (Map.Entry<UUID, Held> entry : frozen.entrySet()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

				if (player != null) {
					arm(player, entry.getValue().options());
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Held held = frozen.get(handler.getPlayer().getUuid());

			if (held != null) {
				arm(handler.getPlayer(), held.options());
			}
		});
	}

	private static void arm(ServerPlayerEntity player, Options options) {
		PlayerEvents.control(player, options.head(), options.body(), SESSION_SECONDS);

		if (options.keys()) {
			PlayerEvents.blockAllKeysExcept(player, options.allowed(), SESSION_SECONDS);
		} else {
			PlayerEvents.clearKeys(player);
		}
	}

	public static void freeze(ServerPlayerEntity player, Options options) {
		frozen.put(player.getUuid(), new Held(player.getName().getString(), options));
		arm(player, options);
		LoreDevLog.info("Frozen: {} ({})",
				player.getName().getString(), options.describe());
	}

	public static void unfreeze(ServerPlayerEntity player) {
		frozen.remove(player.getUuid());
		PlayerEvents.release(player);
		PlayerEvents.clearKeys(player);
		LoreDevLog.info("Unfrozen: {}", player.getName().getString());
	}

	public static boolean isFrozen(ServerPlayerEntity player) {
		return frozen.containsKey(player.getUuid());
	}

	public static java.util.List<String> list() {
		return frozen.values().stream()
				.map(held -> held.name() + " (" + held.options().describe() + ")")
				.toList();
	}
}
