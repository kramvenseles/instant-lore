package com.rufana.instantlore.server.geoguessr;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public final class IslandSlots {

	public record SlotState(int slot, int span, int padX, int padY, int padZ,
	                        int answerX, int answerZ, int half) {
	}

	public interface LoadProgress {
		void at(int done, int total);
	}

	private static final int CELL = 32;

	private static final int ORIGIN = 128;

	private final MinecraftServer server;
	private final PointCatalog catalog;
	private final ExecutorService cutExecutor =
			Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "geoguessr-cutter");
				t.setDaemon(true);
				return t;
			});

	private GeoSource source;
	private final Map<Integer, SlotState> loaded = new HashMap<>();

	private final List<BooleanSupplier> tickTasks = new ArrayList<>();

	public IslandSlots(MinecraftServer server, PointCatalog catalog) {
		this.server = server;
		this.catalog = catalog;
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (tickTasks.isEmpty()) {
				return;
			}
			List<BooleanSupplier> batch = new ArrayList<>(tickTasks);
			tickTasks.clear();
			for (BooleanSupplier t : batch) {
				if (!t.getAsBoolean()) {
					tickTasks.add(t);
				}
			}
		});
	}

	private GeoSource source() {
		if (source == null) {
			source = new GeoSource(server, GeoDimension.sourceBase(server));
		}
		return source;
	}

	public CompletableFuture<SlotState> generate(ServerWorld geo, long warmKey, int half) {
		return generate(geo, warmKey, half, null);
	}

	public CompletableFuture<SlotState> generate(ServerWorld geo, long warmKey, int half, LoadProgress progress) {
		int cx = GeoChunkKey.cx(warmKey), cz = GeoChunkKey.cz(warmKey);
		CompletableFuture<SlotState> out = new CompletableFuture<>();
		PointCatalog.Island cached = catalog.island(cx, cz, half);
		if (cached != null) {
			loadExisting(geo, cached, cx, cz, half, progress, out);
		} else {
			int slot = catalog.takeNextSlot();
			cutThenLoad(geo, warmKey, half, slot, progress, out);
		}
		return out;
	}

	public void release(ServerWorld geo, int slot) {
		SlotState st = loaded.remove(slot);
		if (st != null) {
			removeTickets(geo, slot, st.span());
		}
	}

	public void releaseAll(ServerWorld geo) {
		for (Map.Entry<Integer, SlotState> e : new HashMap<>(loaded).entrySet()) {
			removeTickets(geo, e.getKey(), e.getValue().span());
		}
		loaded.clear();
	}

	public void shutdown() {
		cutExecutor.shutdownNow();
		if (source != null) {
			try {
				source.close();
			} catch (Exception ignored) {
			}
			source = null;
		}
	}


	private void cutThenLoad(ServerWorld geo, long warmKey, int half, int slot,
	                         LoadProgress progress, CompletableFuture<SlotState> out) {
		int cx = GeoChunkKey.cx(warmKey), cz = GeoChunkKey.cz(warmKey);
		int[] cell = cellOf(slot);
		int dcx0 = cell[0], dcz0 = cell[1];
		cutExecutor.submit(() -> {
			try {
				IslandCutter.CutResult r = IslandCutter.cut(source(), geo, cx, cz, half, slot, dcx0, dcz0);
				int minCx = cx - half, minCz = cz - half, span = 2 * half;
				int dBlockX = (dcx0 - minCx) * 16, dBlockZ = (dcz0 - minCz) * 16;
				List<NbtCompound> decor = IslandDecor.collect(source(), minCx, minCz, span, dBlockX, dBlockZ);
				server.execute(() -> loadAndFinish(geo, r, cx, cz, half, decor, progress, out));
			} catch (Throwable t) {
				InstantLore.LOGGER.error("geoguessr: island cutting failed (slot {})", slot, t);
				out.completeExceptionally(t);
			}
		});
	}

	private void loadExisting(ServerWorld geo, PointCatalog.Island isl, int cx, int cz, int half,
	                          LoadProgress progress, CompletableFuture<SlotState> out) {
		try {
			int span = 2 * half, slot = isl.slot();
			SlotState st = new SlotState(slot, span, isl.padX(), isl.padY(), isl.padZ(),
					cx * 16 + 8, cz * 16 + 8, half);
			addTicketsThrottled(geo, slot, span, progress,
					() -> waitLoaded(geo, slot, span, progress, () -> {
						loaded.put(slot, st);
						out.complete(st);
					}));
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: loading prepared island failed (slot {})", isl.slot(), t);
			out.completeExceptionally(t);
		}
	}

	private static int[] cellOf(int slot) {
		int gx = 0, gz = 0, dx = 1, dz = 0, seg = 1, stepInSeg = 0, turns = 0;
		for (int i = 0; i < slot; i++) {
			gx += dx;
			gz += dz;
			if (++stepInSeg == seg) {
				stepInSeg = 0;
				int ndx = -dz, ndz = dx;
				dx = ndx;
				dz = ndz;
				if (++turns % 2 == 0) {
					seg++;
				}
			}
		}
		return new int[]{(ORIGIN + gx) * CELL, (ORIGIN + gz) * CELL};
	}

	private static final int TICKETS_PER_TICK = 4;

	private void loadAndFinish(ServerWorld geo, IslandCutter.CutResult r, int cx, int cz,
	                           int half, List<NbtCompound> decor, LoadProgress progress,
	                           CompletableFuture<SlotState> out) {
		try {
			addTicketsThrottled(geo, r.slot(), r.span(), progress,
					() -> waitLoaded(geo, r.slot(), r.span(), progress,
							() -> finishLoaded(geo, r, cx, cz, half, decor, out)));
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: island load failed (slot {})", r.slot(), t);
			out.completeExceptionally(t);
		}
	}

	private void reportProgress(LoadProgress progress, ServerWorld geo, int slot, int span) {
		if (progress != null) {
			progress.at(countLoaded(geo, slot, span), span * span);
		}
	}

	private void addTicketsThrottled(ServerWorld geo, int slot, int span, LoadProgress progress, Runnable next) {
		ServerChunkManager cm = geo.getChunkManager();
		int[] cell = cellOf(slot);
		int dcx0 = cell[0], dcz0 = cell[1];
		int total = span * span;
		int[] i = {0};
		tickTasks.add(() -> {
			for (int k = 0; k < TICKETS_PER_TICK && i[0] < total; k++, i[0]++) {
				int lx = i[0] % span, lz = i[0] / span;
				cm.addTicket(ChunkTicketType.FORCED, new ChunkPos(dcx0 + lx, dcz0 + lz), 1);
			}
			reportProgress(progress, geo, slot, span);
			if (i[0] >= total) {
				next.run();
				return true;
			}
			return false;
		});
	}

	private void waitLoaded(ServerWorld geo, int slot, int span, LoadProgress progress, Runnable done) {
		int[] attempts = {0};
		tickTasks.add(() -> {
			if (allChunksLoaded(geo, slot, span)) {
				if (progress != null) {
					progress.at(span * span, span * span); // 100%
				}
				done.run();
				return true;
			}
			reportProgress(progress, geo, slot, span);
			if (++attempts[0] > 600) {
				InstantLore.LOGGER.warn("geoguessr: slot {} did not finish loading in 600 ticks - continuing", slot);
				done.run();
				return true;
			}
			return false;
		});
	}

	private int countLoaded(ServerWorld geo, int slot, int span) {
		ServerChunkManager cm = geo.getChunkManager();
		int[] cell = cellOf(slot);
		int dcx0 = cell[0], dcz0 = cell[1];
		int n = 0;
		for (int lz = 0; lz < span; lz++) {
			for (int lx = 0; lx < span; lx++) {
				if (cm.isChunkLoaded(dcx0 + lx, dcz0 + lz)) {
					n++;
				}
			}
		}
		return n;
	}

	private boolean allChunksLoaded(ServerWorld geo, int slot, int span) {
		return countLoaded(geo, slot, span) == span * span;
	}

	private void finishLoaded(ServerWorld geo, IslandCutter.CutResult r, int cx, int cz,
	                          int half, List<NbtCompound> decor, CompletableFuture<SlotState> out) {
		try {
			int decorN = IslandDecor.spawn(geo, decor);
			if (decorN > 0) {
				InstantLore.LOGGER.debug("geoguessr: island decor slot {} - spawned {}", r.slot(), decorN);
			}
			int[] spawn = SafeSpawnFinder.find(geo, r.padBlockX(), r.padBlockZ());
			int sx = spawn != null ? spawn[0] : r.padBlockX();
			int sy = spawn != null ? spawn[1] : 100;
			int sz = spawn != null ? spawn[2] : r.padBlockZ();
			if (spawn == null) {
				InstantLore.LOGGER.warn("geoguessr: safe spawn not found (slot {}) - fallback y=100", r.slot());
			}
			SlotState st = new SlotState(r.slot(), r.span(), sx, sy, sz,
					cx * 16 + 8, cz * 16 + 8, half);
			catalog.recordIsland(cx, cz, half, r.slot(), sx, sy, sz);
			loaded.put(r.slot(), st);
			out.complete(st);
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: island load completion failed (slot {})", r.slot(), t);
			out.completeExceptionally(t);
		}
	}

	private void removeTickets(ServerWorld geo, int slot, int span) {
		ServerChunkManager cm = geo.getChunkManager();
		int[] cell = cellOf(slot);
		int dcx0 = cell[0], dcz0 = cell[1];
		for (int lz = 0; lz < span; lz++) {
			for (int lx = 0; lx < span; lx++) {
				cm.removeTicket(ChunkTicketType.FORCED, new ChunkPos(dcx0 + lx, dcz0 + lz), 1);
			}
		}
	}
}
