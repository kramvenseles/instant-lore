package com.rufana.instantlore.probe.waystones;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public final class TeleportProbe {

	private TeleportProbe() {
	}

	public static boolean teleport(ServerPlayerEntity player, ServerWorld target, Vec3d to) {
		return player.teleport(target, to.x, to.y, to.z, Set.of(), player.getYaw(),
				player.getPitch(), false);
	}
}
