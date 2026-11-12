package com.rufana.instantlore.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimboAtmosphere {
	private static final String FOG_SET =
			"{\"color\":\"#22252A\",\"start\":2.0,\"end\":22.0}";

	private static final int CHECK_INTERVAL_TICKS = 10;

	private static final int REASSERT_INTERVAL_TICKS = 20 * 60;

	private static final Map<UUID, Boolean> INSIDE = new ConcurrentHashMap<>();

	private static int ticks;
	private static int reassertTicks;

	private LimboAtmosphere() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LimboAtmosphere::onTick);

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				INSIDE.remove(handler.getPlayer().getUuid()));
	}

	private static void onTick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (Boolean.TRUE.equals(INSIDE.get(player.getUuid()))) {
				LimboMapBlindness.enforce(player);
			}
		}

		if (++reassertTicks >= REASSERT_INTERVAL_TICKS) {
			reassertTicks = 0;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (Boolean.TRUE.equals(INSIDE.get(player.getUuid()))) {
					sendAtmosphere(player);
					MapPolicy.send(player);
				}
			}
		}

		if (++ticks < CHECK_INTERVAL_TICKS) {
			return;
		}

		ticks = 0;

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean now = player.getEntityWorld().getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
			Boolean was = INSIDE.put(player.getUuid(), now);

			if (Boolean.valueOf(now).equals(was)) {
				continue;
			}

			if (now) {
				enter(player);
			} else if (was != null) {
				leave(player);
			}
		}
	}

	private static void enter(ServerPlayerEntity player) {
		sendAtmosphere(player);
		LimboMapBlindness.enforce(player);
		MapPolicy.send(player);
		LimboModAudit.record(player);
		LoreDevLog.info("Limbo atmosphere ON: {}", player.getName().getString());
	}

	private static void sendAtmosphere(ServerPlayerEntity player) {
		PlayerEvents.send(player, "fog.set", FOG_SET);
		PlayerEvents.send(player, "desaturate.on", "{}");
		PlayerEvents.send(player, "sky.hide", "{}");
		PlayerEvents.send(player, "coords.scramble", "{}");
		PlayerEvents.send(player, "overlay.suppress", "{}");
	}

	private static void leave(ServerPlayerEntity player) {
		PlayerEvents.send(player, "fog.clear", "{}");
		PlayerEvents.send(player, "desaturate.off", "{}");
		PlayerEvents.send(player, "sky.show", "{}");
		PlayerEvents.send(player, "coords.clear", "{}");
		PlayerEvents.send(player, "overlay.restore", "{}");
		LimboMapBlindness.clear(player);
		MapPolicy.send(player);
		LoreDevLog.info("Limbo atmosphere OFF: {}", player.getName().getString());
	}
}
