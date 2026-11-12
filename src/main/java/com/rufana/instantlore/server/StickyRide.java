package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StickyRide {

	private static final Map<UUID, UUID> RIDERS = new ConcurrentHashMap<>();

	private static final Set<UUID> RELEASING = ConcurrentHashMap.newKeySet();

	private static final int CHECK_EVERY = 10;

	private static int tick;

	private static volatile boolean mounting;

	public static boolean mounting() {
		return mounting;
	}

	private StickyRide() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RIDERS.isEmpty() || ++tick % CHECK_EVERY != 0) {
				return;
			}
			reconcile(server);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				reseatIfNeeded(server, handler.player));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID gone = handler.player.getUuid();
			RIDERS.entrySet().removeIf(entry -> {
				if (!entry.getValue().equals(gone)) {
					return false;
				}
				InstantLore.LOGGER.info("[mount] carrier left - passenger {} dismounted", entry.getKey());
				return true;
			});
		});
	}

	public static boolean mount(ServerPlayerEntity rider, ServerPlayerEntity carrier) {
		if (rider == null || carrier == null || rider.getUuid().equals(carrier.getUuid())) {
			return false;
		}
		if (ridesTransitively(carrier.getUuid(), rider.getUuid())) {
			return false;
		}
		RIDERS.put(rider.getUuid(), carrier.getUuid());
		boolean seated = seat(rider, carrier);
		InstantLore.LOGGER.info("[mount] {} rides {}{}", rider.getName().getString(),
				carrier.getName().getString(), seated ? "" : " (will mount with verification)");
		return true;
	}

	public static boolean release(ServerPlayerEntity rider) {
		if (rider == null || RIDERS.remove(rider.getUuid()) == null) {
			return false;
		}
		RELEASING.add(rider.getUuid());
		try {
			rider.stopRiding();
		} finally {
			RELEASING.remove(rider.getUuid());
		}
		return true;
	}

	public static boolean holds(UUID rider) {
		return rider != null && RIDERS.containsKey(rider) && !RELEASING.contains(rider);
	}

	public static Map<UUID, UUID> snapshot() {
		return Map.copyOf(RIDERS);
	}

	private static void reconcile(MinecraftServer server) {
		List<UUID> orphaned = new ArrayList<>();
		for (Map.Entry<UUID, UUID> entry : RIDERS.entrySet()) {
			ServerPlayerEntity rider = server.getPlayerManager().getPlayer(entry.getKey());
			ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(entry.getValue());
			if (carrier == null) {
				orphaned.add(entry.getKey());
				continue;
			}
			if (rider == null) {
				continue;
			}
			if (rider.getVehicle() != carrier) {
				seat(rider, carrier);
			}
		}
		orphaned.forEach(RIDERS::remove);
	}

	private static void reseatIfNeeded(MinecraftServer server, ServerPlayerEntity player) {
		UUID carrierId = RIDERS.get(player.getUuid());
		if (carrierId == null) {
			return;
		}
		ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(carrierId);
		if (carrier == null) {
			RIDERS.remove(player.getUuid());
			return;
		}
		seat(player, carrier);
	}

	private static boolean seat(ServerPlayerEntity rider, ServerPlayerEntity carrier) {
		try {
			if (rider.getEntityWorld() != carrier.getEntityWorld()) {
				rider.teleport(carrier.getEntityWorld(), carrier.getX(), carrier.getY(), carrier.getZ(),
						Set.of(), carrier.getYaw(), carrier.getPitch(), false);
			}
			mounting = true;
			try {
				return rider.startRiding(carrier, true, true);
			} finally {
				mounting = false;
			}
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[mount] failed to seat {} on {}",
					rider.getName().getString(), carrier.getName().getString(), t);
			return false;
		}
	}

	private static boolean ridesTransitively(UUID who, UUID target) {
		UUID current = who;
		for (int hops = 0; hops < 16 && current != null; hops++) {
			if (current.equals(target)) {
				return true;
			}
			current = RIDERS.get(current);
		}
		return false;
	}
}
