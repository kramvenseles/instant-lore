package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OfferingEvents {
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final List<RiteListener> RITE_LISTENERS = new CopyOnWriteArrayList<>();

	private OfferingEvents() {
	}

	@FunctionalInterface
	public interface Listener {
		void onFull(ServerWorld world, BlockPos pos, String type, String title,
				ServerPlayerEntity by, Map<String, Integer> need, Map<String, Integer> have);
	}

	public static void subscribe(Listener listener) {
		LISTENERS.add(listener);
	}

	@FunctionalInterface
	public interface RiteListener {
		void onRite(MinecraftServer server, String rite, String kind, String type,
				String dimension, BlockPos gate, ServerPlayerEntity by);
	}

	public static void subscribeRite(RiteListener listener) {
		RITE_LISTENERS.add(listener);
	}

	public static void fireRite(MinecraftServer server, String rite, String kind, String type,
			String dimension, BlockPos gate, ServerPlayerEntity by) {
		for (RiteListener listener : RITE_LISTENERS) {
			try {
				listener.onRite(server, rite, kind, type, dimension, gate, by);
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Ritual handler '{}' ({}) failed", rite, type, e);
			}
		}
	}

	public static void fire(ServerWorld world, BlockPos pos, String type, String title,
			ServerPlayerEntity by, Map<String, Integer> need, Map<String, Integer> have) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onFull(world, pos, type, title, by, need, have);
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Offering event handler '{}' failed", type, e);
			}
		}
	}
}
