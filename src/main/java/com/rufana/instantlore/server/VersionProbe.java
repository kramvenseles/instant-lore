package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.ServerContentState;
import com.rufana.instantlore.net.ContentVersionPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VersionProbe {

	private static final int INTERVAL_TICKS = 100;

	private static final int MAX_ATTEMPTS = 3;

	private static final Map<UUID, Pending> WAITING = new ConcurrentHashMap<>();

	private record Pending(int ticksLeft, int attemptsLeft) {
	}

	private VersionProbe() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VersionProbe::onTick);
	}

	public static void expect(ServerPlayerEntity player) {
		WAITING.put(player.getUuid(), new Pending(INTERVAL_TICKS, MAX_ATTEMPTS));
	}

	public static void forget(UUID player) {
		if (player != null) {
			WAITING.remove(player);
		}
	}

	private static void onTick(net.minecraft.server.MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Pending> entry : WAITING.entrySet()) {
			UUID uuid = entry.getKey();
			Pending pending = entry.getValue();

			if (ClientVersions.reported(uuid) != null) {
				WAITING.remove(uuid);
				continue;
			}

			if (pending.ticksLeft() > 1) {
				WAITING.put(uuid, new Pending(pending.ticksLeft() - 1, pending.attemptsLeft()));
				continue;
			}

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

			if (player == null) {
				WAITING.remove(uuid);
				continue;
			}

			if (pending.attemptsLeft() <= 1) {
				WAITING.remove(uuid);
				LoreDevLog.info("{} did not report a version after {} attempts - treating the silence as genuine",
						player.getName().getString(), MAX_ATTEMPTS);
				continue;
			}

			try {
				ServerPlayNetworking.send(player, new ContentVersionPayload(ServerContentState.version()));
			} catch (Throwable e) {
				InstantLore.LOGGER.warn("Failed to re-query version from {}",
						player.getName().getString(), e);
			}

			WAITING.put(uuid, new Pending(INTERVAL_TICKS, pending.attemptsLeft() - 1));
		}
	}
}
