package com.rufana.instantlore.server;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreItemDefinition;
import com.rufana.instantlore.net.LoreEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoreItemBus {
	private static final int PER_SECOND = 8;

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, Bucket> BUCKETS = new ConcurrentHashMap<>();

	private LoreItemBus() {
	}

	@FunctionalInterface
	public interface Listener {
		void onUse(ServerPlayerEntity player, int slot, LoreItemDefinition definition,
				Hand hand, BlockPos pos);
	}

	public static void subscribe(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void used(ServerPlayerEntity player, int slot, LoreItemDefinition definition,
			Hand hand, BlockPos pos) {
		if (!allow(player)) {
			return;
		}

		sendEvent(player, slot, definition, hand, pos);

		for (Listener listener : LISTENERS) {
			try {
				listener.onUse(player, slot, definition, hand, pos);
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Item use handler for {} failed", slot, e);
			}
		}
	}

	private static void sendEvent(ServerPlayerEntity player, int slot, LoreItemDefinition definition,
			Hand hand, BlockPos pos) {
		JsonObject data = new JsonObject();
		data.addProperty("slot", slot);
		data.addProperty("name", definition.name());
		data.addProperty("hand", hand == Hand.OFF_HAND ? "off" : "main");

		if (pos != null) {
			data.addProperty("x", pos.getX());
			data.addProperty("y", pos.getY());
			data.addProperty("z", pos.getZ());
		}

		ServerPlayNetworking.send(player, new LoreEventPayload("item.use", data.toString()));
	}

	private static boolean allow(ServerPlayerEntity player) {
		return BUCKETS.computeIfAbsent(player.getUuid(), uuid -> new Bucket()).allow();
	}

	public static void forget(ServerPlayerEntity player) {
		BUCKETS.remove(player.getUuid());
	}

	private static final class Bucket {
		private long windowStart = System.nanoTime();
		private int used;

		synchronized boolean allow() {
			long now = System.nanoTime();

			if (now - windowStart >= 1_000_000_000L) {
				windowStart = now;
				used = 0;
			}

			if (used >= PER_SECOND) {
				return false;
			}

			used++;
			return true;
		}
	}
}
