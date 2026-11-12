package com.rufana.instantlore.server.geoguessr;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class SafeSpawnFinder {

	private static final int TOP = 316;
	private static final int BOTTOM = -63;
	private static final int SEARCH = 6;

	private SafeSpawnFinder() {
	}

	public static int[] find(ServerWorld geo, int x, int z) {
		int[] dry = scan(geo, x, z, true);
		if (dry != null) {
			return dry;
		}
		int[] wet = scan(geo, x, z, false);
		if (wet == null) {
			return null;
		}
		int surfaceY = waterSurface(geo, wet[0], wet[2], wet[1]);
		if (surfaceY == Integer.MIN_VALUE) {
			return wet;
		}
		geo.setBlockState(new BlockPos(wet[0], surfaceY, wet[2]), Blocks.BARRIER.getDefaultState());
		return new int[]{wet[0], surfaceY + 1, wet[2]};
	}

	private static int[] scan(ServerWorld geo, int x, int z, boolean requireDry) {
		for (int r = 0; r <= SEARCH; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int y = columnY(geo, x + dx, z + dz, requireDry);
					if (y != Integer.MIN_VALUE) {
						return new int[]{x + dx, y, z + dz};
					}
				}
			}
		}
		return null;
	}

	private static int columnY(ServerWorld geo, int x, int z, boolean requireDry) {
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int y = TOP; y > BOTTOM; y--) {
			if (!geo.getBlockState(p.set(x, y, z)).blocksMovement()) {
				continue;
			}
			if (geo.getBlockState(p.set(x, y + 1, z)).blocksMovement()
					|| geo.getBlockState(p.set(x, y + 2, z)).blocksMovement()) {
				continue;
			}
			if (requireDry
					&& (!geo.getFluidState(p.set(x, y + 1, z)).isEmpty()
					|| !geo.getFluidState(p.set(x, y + 2, z)).isEmpty())) {
				return Integer.MIN_VALUE;
			}
			return y + 1;
		}
		return Integer.MIN_VALUE;
	}

	private static int waterSurface(ServerWorld geo, int x, int z, int fromY) {
		BlockPos.Mutable p = new BlockPos.Mutable();
		int surface = Integer.MIN_VALUE;
		for (int y = fromY; y <= TOP; y++) {
			if (!geo.getFluidState(p.set(x, y, z)).isIn(FluidTags.WATER)) {
				break;
			}
			surface = y;
		}
		return surface;
	}
}
