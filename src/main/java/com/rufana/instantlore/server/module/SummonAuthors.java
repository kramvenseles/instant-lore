package com.rufana.instantlore.server.module;

import com.rufana.instantlore.server.LimboTransfer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SummonAuthors {

	private static final double NEAR = 12.0;

	private static final int MAX = 512;

	private static final Map<UUID, UUID> AUTHORS = java.util.Collections.synchronizedMap(
			new LinkedHashMap<>(64, 0.75f, false) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<UUID, UUID> eldest) {
					return size() > MAX;
				}
			});

	private SummonAuthors() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY) || !interesting(entity)) {
				return;
			}
			net.minecraft.entity.player.PlayerEntity nearest = world.getClosestPlayer(entity, NEAR);
			if (nearest instanceof ServerPlayerEntity player) {
				AUTHORS.put(entity.getUuid(), player.getUuid());
			}
		});
	}

	private static boolean interesting(Entity entity) {
		EntityType<?> type = entity.getType();
		return type == EntityType.CHICKEN || type == EntityType.SNOW_GOLEM
				|| type == EntityType.IRON_GOLEM || type == EntityType.WITHER;
	}

	public static UUID of(UUID entity) {
		return AUTHORS.get(entity);
	}

	public static UUID common(Iterable<UUID> entities) {
		UUID author = null;
		for (UUID entity : entities) {
			UUID candidate = of(entity);
			if (candidate == null) {
				return null;
			}
			if (author == null) {
				author = candidate;
			} else if (!author.equals(candidate)) {
				return null;
			}
		}
		return author;
	}

	public static void forget(Iterable<UUID> entities) {
		for (UUID entity : entities) {
			AUTHORS.remove(entity);
		}
	}
}
