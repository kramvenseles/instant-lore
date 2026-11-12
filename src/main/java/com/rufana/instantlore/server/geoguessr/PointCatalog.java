package com.rufana.instantlore.server.geoguessr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PointCatalog {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve(InstantLore.MOD_ID).resolve("geo-points.json");

	public record Island(int slot, int padX, int padY, int padZ) {
	}

	private final Set<Long> used = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<String, Island> islands = new ConcurrentHashMap<>();
	private final AtomicInteger next = new AtomicInteger(0);

	private PointCatalog() {
	}

	private static String key(int cx, int cz, int half) {
		return cx + "," + cz + "," + half;
	}

	public static PointCatalog load() {
		PointCatalog c = new PointCatalog();
		try {
			if (Files.exists(FILE)) {
				JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
				if (root.isJsonArray()) {
					for (JsonElement el : root.getAsJsonArray()) {
						JsonArray pair = el.getAsJsonArray();
						c.used.add(GeoChunkKey.of(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
					}
				} else {
					JsonObject o = root.getAsJsonObject();
					c.next.set(o.has("next") ? o.get("next").getAsInt() : 0);
					if (o.has("used")) {
						for (JsonElement el : o.getAsJsonArray("used")) {
							JsonArray pair = el.getAsJsonArray();
							c.used.add(GeoChunkKey.of(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
						}
					}
					if (o.has("islands")) {
						for (JsonElement el : o.getAsJsonArray("islands")) {
							JsonObject i = el.getAsJsonObject();
							c.islands.put(key(i.get("cx").getAsInt(), i.get("cz").getAsInt(), i.get("half").getAsInt()),
									new Island(i.get("slot").getAsInt(), i.get("px").getAsInt(),
											i.get("py").getAsInt(), i.get("pz").getAsInt()));
						}
					}
				}
			}
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: geo-points.json not parsed - registry is empty", t);
		}
		return c;
	}


	public boolean usedPoint(long key) {
		return used.contains(key);
	}

	public int size() {
		return used.size();
	}

	public void markUsed(long key) {
		if (used.add(key)) {
			save();
		}
	}

	public void reset() {
		used.clear();
		save();
	}


	public Island island(int cx, int cz, int half) {
		return islands.get(key(cx, cz, half));
	}

	public int takeNextSlot() {
		int slot = next.getAndIncrement();
		save();
		return slot;
	}

	public void recordIsland(int cx, int cz, int half, int slot, int px, int py, int pz) {
		islands.put(key(cx, cz, half), new Island(slot, px, py, pz));
		save();
	}

	private synchronized void save() {
		try {
			JsonObject root = new JsonObject();
			root.addProperty("next", next.get());
			JsonArray usedArr = new JsonArray();
			for (long k : used) {
				JsonArray pair = new JsonArray();
				pair.add(GeoChunkKey.cx(k));
				pair.add(GeoChunkKey.cz(k));
				usedArr.add(pair);
			}
			root.add("used", usedArr);
			JsonArray islArr = new JsonArray();
			islands.forEach((k, isl) -> {
				String[] p = k.split(",");
				JsonObject i = new JsonObject();
				i.addProperty("cx", Integer.parseInt(p[0]));
				i.addProperty("cz", Integer.parseInt(p[1]));
				i.addProperty("half", Integer.parseInt(p[2]));
				i.addProperty("slot", isl.slot());
				i.addProperty("px", isl.padX());
				i.addProperty("py", isl.padY());
				i.addProperty("pz", isl.padZ());
				islArr.add(i);
			});
			root.add("islands", islArr);

			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: geo-points.json not saved", t);
		}
	}
}
