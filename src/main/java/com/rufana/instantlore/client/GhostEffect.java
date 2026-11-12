package com.rufana.instantlore.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

import java.util.Random;

public final class GhostEffect {

	private static final Random RANDOM = new Random();

	private static final int PER_TICK = 3;

	private GhostEffect() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(GhostEffect::tick);
	}

	private static void tick(MinecraftClient client) {
		if (ClientGhosts.isEmpty()) {
			return;
		}
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			if (player == client.player || !ClientGhosts.contains(player.getUuid())) {
				continue;
			}
			spawnAround(world, player);
		}
	}

	private static void spawnAround(ClientWorld world, AbstractClientPlayerEntity player) {
		double height = player.getHeight();
		for (int i = 0; i < PER_TICK; i++) {
			double x = player.getX() + (RANDOM.nextDouble() - 0.5) * player.getWidth();
			double y = player.getY() + RANDOM.nextDouble() * height * 0.5;
			double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * player.getWidth();
			world.addParticleClient(ParticleTypes.CLOUD, x, y, z,
					0.0, 0.02, 0.0);
		}
	}
}
