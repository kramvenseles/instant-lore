package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

public final class LimboExile {
	private static final double SINK_PER_TICK = 0.2;

	private static final double REMOVE_DEPTH = 26.0;

	private static final double FLOOR_TOP_Y = 1.0;

	private static final double BASE_RISE = 6.0;

	private static final double MAX_RISE = 28.0;

	private static final int PLUME_SAMPLES = 14;

	private LimboExile() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (!world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY)) {
				return;
			}
			java.util.List<Entity> toExile = new java.util.ArrayList<>();
			for (Entity entity : world.iterateEntities()) {
				if (entity == null) {
					continue;
				}
				if (entity instanceof PlayerEntity || entity instanceof ItemEntity
						|| entity instanceof ArmorStandEntity || entity instanceof ProjectileEntity
						|| entity instanceof BlockAttachedEntity
						|| isOurs(entity)) {
					continue;
				}
				if (com.rufana.instantlore.server.module.HarvestModule.isAscending(entity.getUuid())) {
					continue;
				}
				toExile.add(entity);
			}
			for (Entity entity : toExile) {
				exile(world, entity);
			}
		});
	}

	private static boolean isOurs(Entity entity) {
		return EntityType.getId(entity.getType()).getNamespace().equals(InstantLore.MOD_ID);
	}

	private static void exile(ServerWorld world, Entity entity) {
		if (entity.getY() < FLOOR_TOP_Y - REMOVE_DEPTH) {
			entity.discard();
			return;
		}

		entity.noClip = true;

		if (entity instanceof MobEntity mob) {
			mob.setAiDisabled(true);
		}

		entity.setPosition(entity.getX(), entity.getY() - SINK_PER_TICK, entity.getZ());

		if (!com.rufana.instantlore.server.module.HarvestModule.isLinked(entity.getUuid())) {
			soulPlume(world, entity);
		}
	}

	private static void soulPlume(ServerWorld world, Entity entity) {
		double x = entity.getX();
		double z = entity.getZ();
		double base = Math.max(entity.getY() + 0.3, FLOOR_TOP_Y);
		double depth = Math.max(0.0, FLOOR_TOP_Y - entity.getY());
		double rise = Math.min(BASE_RISE + depth, MAX_RISE);

		for (int i = 0; i < PLUME_SAMPLES; i++) {
			double t = (i + world.getRandom().nextDouble()) / PLUME_SAMPLES;
			double y = base + rise * t;
			double jitterX = (world.getRandom().nextDouble() - 0.5) * 0.12;
			double jitterZ = (world.getRandom().nextDouble() - 0.5) * 0.12;
			double up = 0.20 + world.getRandom().nextDouble() * 0.12;
			world.spawnParticles(ParticleTypes.SOUL, x + jitterX, y, z + jitterZ, 0, 0.0, up, 0.0, 1.0);
		}
	}
}
