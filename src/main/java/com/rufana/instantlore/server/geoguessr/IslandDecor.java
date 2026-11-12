package com.rufana.instantlore.server.geoguessr;

import com.rufana.instantlore.InstantLore;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LoadedEntityProcessor;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class IslandDecor {

	private static final Set<String> DECOR = Set.of(
			"minecraft:item_frame",
			"minecraft:glow_item_frame",
			"minecraft:painting",
			"minecraft:armor_stand");

	private IslandDecor() {
	}

	public static List<NbtCompound> collect(GeoSource src, int minCx, int minCz, int span,
	                                        int dBlockX, int dBlockZ) {
		List<NbtCompound> out = new ArrayList<>();
		int bx0 = minCx * 16, bx1 = (minCx + span) * 16 - 1;
		int bz0 = minCz * 16, bz1 = (minCz + span) * 16 - 1;
		for (int lcz = 0; lcz < span; lcz++) {
			for (int lcx = 0; lcx < span; lcx++) {
				NbtCompound chunk = src.entityChunk(minCx + lcx, minCz + lcz);
				if (chunk == null) {
					continue;
				}
				NbtList ents = chunk.getListOrEmpty("Entities");
				for (int i = 0; i < ents.size(); i++) {
					NbtCompound e = ents.getCompoundOrEmpty(i);
					if (!DECOR.contains(e.getString("id", ""))) {
						continue;
					}
					if (onRing(e, bx0, bx1, bz0, bz1)) {
						continue;
					}
					shiftEntity(e, dBlockX, dBlockZ);
					out.add(e);
				}
			}
		}
		return out;
	}

	private static boolean onRing(NbtCompound e, int bx0, int bx1, int bz0, int bz1) {
		int ex, ez;
		int[] bp = e.getIntArray("block_pos").orElse(null);
		if (bp != null && bp.length == 3) {
			ex = bp[0];
			ez = bp[2];
		} else if (e.contains("TileX")) {
			ex = e.getInt("TileX", 0);
			ez = e.getInt("TileZ", 0);
		} else {
			NbtList pos = e.getListOrEmpty("Pos");
			if (pos.size() != 3) {
				return false;
			}
			ex = (int) Math.floor(pos.getDouble(0, 0));
			ez = (int) Math.floor(pos.getDouble(2, 0));
		}
		return ex == bx0 || ex == bx1 || ez == bz0 || ez == bz1;
	}

	private static void shiftEntity(NbtCompound e, int dBlockX, int dBlockZ) {
		NbtList pos = e.getListOrEmpty("Pos");
		if (pos.size() == 3) {
			pos.set(0, NbtDouble.of(pos.getDouble(0, 0) + dBlockX));
			pos.set(2, NbtDouble.of(pos.getDouble(2, 0) + dBlockZ));
		}
		int[] bp = e.getIntArray("block_pos").orElse(null);
		if (bp != null && bp.length == 3) {
			e.put("block_pos", new NbtIntArray(new int[]{bp[0] + dBlockX, bp[1], bp[2] + dBlockZ}));
		}
		if (e.contains("TileX")) {
			e.putInt("TileX", e.getInt("TileX", 0) + dBlockX);
		}
		if (e.contains("TileZ")) {
			e.putInt("TileZ", e.getInt("TileZ", 0) + dBlockZ);
		}
		e.put("UUID", new NbtIntArray(freshUuid()));
	}

	private static int[] freshUuid() {
		UUID u = UUID.randomUUID();
		long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
		return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
	}

	public static int spawn(ServerWorld geo, List<NbtCompound> decor) {
		int n = 0;
		for (NbtCompound nbt : decor) {
			try {
				Entity ent = EntityType.loadEntityWithPassengers(nbt, geo, SpawnReason.STRUCTURE,
						LoadedEntityProcessor.NOOP);
				if (ent != null && geo.spawnEntity(ent)) {
					n++;
				}
			} catch (Throwable t) {
				InstantLore.LOGGER.warn("geoguessr: decor entity failed to spawn", t);
			}
		}
		return n;
	}
}
