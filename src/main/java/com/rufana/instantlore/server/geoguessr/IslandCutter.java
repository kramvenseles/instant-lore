package com.rufana.instantlore.server.geoguessr;

import com.rufana.instantlore.InstantLore;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtShort;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.VersionedChunkStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class IslandCutter {

	private static final String AIR = "minecraft:air";
	private static final String BEDROCK = "minecraft:bedrock";
	private static final String BARRIER = "minecraft:barrier";

	private static final java.util.Set<String> PORTAL_BLOCKS = java.util.Set.of(
			"minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway");

	private static final int Y_MIN = -64;
	private static final int CEILING_Y = 318;

	private IslandCutter() {
	}

	public record CutResult(int slot, int span, int padBlockX, int padBlockZ, int chunksWritten) {
	}

	private record Edit(int bi, String name, boolean onlyAir) {
	}

	public static CutResult cut(GeoSource src, ServerWorld geo, int centerCx, int centerCz,
	                            int half, int slot, int dcx0, int dcz0) {
		int minCx = centerCx - half, minCz = centerCz - half;
		int span = 2 * half;
		int dBlockX = (dcx0 - minCx) * 16;
		int dBlockZ = (dcz0 - minCz) * 16;

		VersionedChunkStorage target = geo.getChunkManager().chunkLoadingManager;
		List<CompletableFuture<Void>> writes = new ArrayList<>();
		int written = 0, spawnersOff = 0, portalsOff = 0, containersEmptied = 0;

		for (int lcz = 0; lcz < span; lcz++) {
			for (int lcx = 0; lcx < span; lcx++) {
				int scx = minCx + lcx, scz = minCz + lcz;
				NbtCompound root = src.chunk(scx, scz);
				if (root == null) {
					continue;
				}
				int dcx = dcx0 + lcx, dcz = dcz0 + lcz;
				root.putInt("xPos", dcx);
				root.putInt("zPos", dcz);
				shiftCoords(root.getListOrEmpty("block_entities"), dBlockX, dBlockZ);
				shiftCoords(root.getListOrEmpty("block_ticks"), dBlockX, dBlockZ);
				shiftCoords(root.getListOrEmpty("fluid_ticks"), dBlockX, dBlockZ);
				spawnersOff += neutralizeSpawners(root.getListOrEmpty("block_entities"));
				portalsOff += neutralizePortals(root.getListOrEmpty("sections"),
						root.getListOrEmpty("block_entities"));
				containersEmptied += emptyContainers(root.getListOrEmpty("block_entities"));
				root.putByte("isLightOn", (byte) 0);
				NbtCompound emptyStruct = new NbtCompound();
				emptyStruct.put("References", new NbtCompound());
				emptyStruct.put("starts", new NbtCompound());
				root.put("structures", emptyStruct);

				boolean onX0 = lcx == 0, onX1 = lcx == span - 1;
				boolean onZ0 = lcz == 0, onZ1 = lcz == span - 1;
				sealChunk(root, onX0, onX1, onZ0, onZ1);

				target.setNbt(new ChunkPos(dcx, dcz), root);
				written++;
			}
		}
		target.completeAll(false).join();

		int padX = dcx0 * 16 + half * 16;
		int padZ = dcz0 * 16 + half * 16;
		InstantLore.LOGGER.debug(
				"geoguessr: island slot {} from s1 ({},{}) half {} - chunks {}, spawners {}, portals {}, containers emptied {}",
				slot, centerCx, centerCz, half, written, spawnersOff, portalsOff, containersEmptied);
		return new CutResult(slot, span, padX, padZ, written);
	}


	private static void sealChunk(NbtCompound root, boolean onX0, boolean onX1,
	                              boolean onZ0, boolean onZ1) {
		boolean edge = onX0 || onX1 || onZ0 || onZ1;
		if (edge) {
			stripRingBlockEntities(root, onX0, onX1, onZ0, onZ1);
		}
		NbtList sections = root.getListOrEmpty("sections");
		Map<Integer, NbtCompound> byY = new HashMap<>();
		NbtCompound biomeTemplate = null;
		for (int i = 0; i < sections.size(); i++) {
			NbtCompound s = sections.getCompoundOrEmpty(i);
			if (s.contains("block_states")) {
				byY.put((int) s.getByte("Y", (byte) 0), s);
			}
			if (biomeTemplate == null && s.contains("biomes")) {
				biomeTemplate = s.getCompoundOrEmpty("biomes");
			}
		}

		Map<Integer, List<Edit>> edits = new HashMap<>();
		if (edge) {
			for (int y = Y_MIN; y <= CEILING_Y; y++) {
				if (onX0) {
					for (int lz = 0; lz < 16; lz++) addEdit(edits, y, lz, 0, BEDROCK, false);
				}
				if (onX1) {
					for (int lz = 0; lz < 16; lz++) addEdit(edits, y, lz, 15, BEDROCK, false);
				}
				if (onZ0) {
					for (int lx = 0; lx < 16; lx++) addEdit(edits, y, 0, lx, BEDROCK, false);
				}
				if (onZ1) {
					for (int lx = 0; lx < 16; lx++) addEdit(edits, y, 15, lx, BEDROCK, false);
				}
			}
		}
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				if ((onX0 && lx == 0) || (onX1 && lx == 15) || (onZ0 && lz == 0) || (onZ1 && lz == 15)) {
					continue;
				}
				addEdit(edits, CEILING_Y, lz, lx, BARRIER, true);
			}
		}

		for (Map.Entry<Integer, List<Edit>> e : edits.entrySet()) {
			int secY = e.getKey();
			NbtCompound section = byY.get(secY);
			if (section == null) {
				section = synthSection(secY, biomeTemplate);
				sections.add(sections.size(), section);
				byY.put(secY, section);
			}
			applySectionEdits(section, e.getValue());
		}
		root.put("sections", sections);
	}

	private static void stripRingBlockEntities(NbtCompound root, boolean onX0, boolean onX1,
	                                           boolean onZ0, boolean onZ1) {
		NbtList be = root.getListOrEmpty("block_entities");
		for (int i = be.size() - 1; i >= 0; i--) {
			NbtCompound e = be.getCompoundOrEmpty(i);
			int lx = e.getInt("x", 0) & 15;
			int lz = e.getInt("z", 0) & 15;
			if ((onX0 && lx == 0) || (onX1 && lx == 15) || (onZ0 && lz == 0) || (onZ1 && lz == 15)) {
				be.remove(i);
			}
		}
	}

	private static void addEdit(Map<Integer, List<Edit>> edits, int y, int lz, int lx,
	                            String name, boolean onlyAir) {
		int secY = y >> 4;
		int yl = y & 15;
		int bi = yl * 256 + lz * 16 + lx;
		edits.computeIfAbsent(secY, k -> new ArrayList<>()).add(new Edit(bi, name, onlyAir));
	}

	private static NbtCompound synthSection(int secY, NbtCompound biomeTemplate) {
		NbtCompound sec = new NbtCompound();
		sec.putByte("Y", (byte) secY);
		NbtCompound bs = new NbtCompound();
		NbtList pal = new NbtList();
		NbtCompound air = new NbtCompound();
		air.putString("Name", AIR);
		pal.add(pal.size(), air);
		bs.put("palette", pal);
		sec.put("block_states", bs);
		if (biomeTemplate != null) {
			sec.put("biomes", biomeTemplate.copy());
		} else {
			NbtCompound biomes = new NbtCompound();
			NbtList bpal = new NbtList();
			bpal.add(bpal.size(), net.minecraft.nbt.NbtString.of("minecraft:plains"));
			biomes.put("palette", bpal);
			sec.put("biomes", biomes);
		}
		return sec;
	}


	private static int secBits(int paletteSize) {
		if (paletteSize <= 1) {
			return 4;
		}
		return Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
	}

	private static void applySectionEdits(NbtCompound section, List<Edit> list) {
		NbtCompound bs = section.getCompoundOrEmpty("block_states");
		NbtList palette = bs.getListOrEmpty("palette");
		int bits = secBits(palette.size());

		int[] idxs;
		long[] data = bs.getLongArray("data").orElse(null);
		if (data != null && palette.size() > 1) {
			idxs = unpack(data, bits);
		} else {
			idxs = new int[4096];
		}

		boolean[] isAir = new boolean[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			isAir[i] = AIR.equals(palette.getCompoundOrEmpty(i).getString("Name", ""));
		}

		for (Edit ed : list) {
			if (ed.onlyAir()) {
				int cur = idxs[ed.bi()];
				if (cur >= isAir.length || !isAir[cur]) {
					continue;
				}
			}
			idxs[ed.bi()] = paletteIndex(palette, ed.name());
		}

		int newBits = secBits(palette.size());
		if (palette.size() > 1) {
			bs.putLongArray("data", pack(idxs, newBits));
		} else {
			bs.remove("data");
		}
	}

	private static int paletteIndex(NbtList palette, String name) {
		for (int i = 0; i < palette.size(); i++) {
			NbtCompound p = palette.getCompoundOrEmpty(i);
			if (name.equals(p.getString("Name", "")) && !p.contains("Properties")) {
				return i;
			}
		}
		NbtCompound c = new NbtCompound();
		c.putString("Name", name);
		palette.add(palette.size(), c);
		return palette.size() - 1;
	}

	private static int[] unpack(long[] data, int bits) {
		int per = 64 / bits;
		long mask = (1L << bits) - 1;
		int[] out = new int[4096];
		int n = 0;
		for (long lv : data) {
			for (int i = 0; i < per; i++) {
				out[n++] = (int) ((lv >>> (i * bits)) & mask);
				if (n >= 4096) {
					return out;
				}
			}
		}
		return out;
	}

	private static long[] pack(int[] idx, int bits) {
		int per = 64 / bits;
		long mask = (1L << bits) - 1;
		int longs = (4096 + per - 1) / per;
		long[] out = new long[longs];
		int i = 0, li = 0;
		while (i < 4096) {
			long v = 0L;
			for (int j = 0; j < per; j++) {
				if (i + j < 4096) {
					v |= (idx[i + j] & mask) << (j * bits);
				}
			}
			out[li++] = v;
			i += per;
		}
		return out;
	}


	private static void shiftCoords(NbtList list, int dx, int dz) {
		for (int i = 0; i < list.size(); i++) {
			NbtCompound e = list.getCompoundOrEmpty(i);
			if (e.contains("x")) {
				e.putInt("x", e.getInt("x", 0) + dx);
			}
			if (e.contains("z")) {
				e.putInt("z", e.getInt("z", 0) + dz);
			}
		}
	}

	private static int neutralizeSpawners(NbtList blockEntities) {
		int n = 0;
		for (int i = 0; i < blockEntities.size(); i++) {
			NbtCompound be = blockEntities.getCompoundOrEmpty(i);
			if ("minecraft:mob_spawner".equals(be.getString("id", ""))) {
				be.put("RequiredPlayerRange", NbtShort.of((short) 0));
				n++;
			}
		}
		return n;
	}

	private static int neutralizePortals(NbtList sections, NbtList blockEntities) {
		int n = 0;
		for (int i = 0; i < sections.size(); i++) {
			NbtList palette = sections.getCompoundOrEmpty(i)
					.getCompoundOrEmpty("block_states").getListOrEmpty("palette");
			for (int p = 0; p < palette.size(); p++) {
				NbtCompound entry = palette.getCompoundOrEmpty(p);
				if (PORTAL_BLOCKS.contains(entry.getString("Name", ""))) {
					entry.putString("Name", AIR);
					entry.remove("Properties");
					n++;
				}
			}
		}
		for (int i = blockEntities.size() - 1; i >= 0; i--) {
			if ("minecraft:end_gateway".equals(blockEntities.getCompoundOrEmpty(i).getString("id", ""))) {
				blockEntities.remove(i);
			}
		}
		return n;
	}

	private static int emptyContainers(NbtList blockEntities) {
		int n = 0;
		for (int i = 0; i < blockEntities.size(); i++) {
			NbtCompound be = blockEntities.getCompoundOrEmpty(i);
			boolean touched = false;
			if (be.contains("Items")) {
				be.put("Items", new NbtList());
				touched = true;
			}
			if (be.contains("LootTable")) {
				be.remove("LootTable");
				be.remove("LootTableSeed");
				touched = true;
			}
			if (be.contains("item")) { // decorated_pot
				be.remove("item");
				touched = true;
			}
			if (be.contains("RecordItem")) { // jukebox
				be.remove("RecordItem");
				touched = true;
			}
			if (touched) {
				n++;
			}
		}
		return n;
	}
}
