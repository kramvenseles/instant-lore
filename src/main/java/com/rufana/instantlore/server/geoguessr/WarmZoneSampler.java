package com.rufana.instantlore.server.geoguessr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.function.LongPredicate;

public final class WarmZoneSampler {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve(InstantLore.MOD_ID).resolve("inhab.json");

	private final long[] warm;
	private final int threshold;

	private WarmZoneSampler(long[] warm, int threshold) {
		this.warm = warm;
		this.threshold = threshold;
	}

	public static WarmZoneSampler loadOrNull() {
		if (!Files.exists(FILE)) {
			return null;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
			JsonObject obj = root.getAsJsonObject();
			int thresh = obj.has("thresh") ? obj.get("thresh").getAsInt() : 72000;
			JsonObject lived = obj.getAsJsonObject("lived");

			int count = 0;
			for (Map.Entry<String, JsonElement> e : lived.entrySet()) {
				count += e.getValue().getAsJsonArray().size();
			}
			long[] warm = new long[count];
			int w = 0;
			for (Map.Entry<String, JsonElement> e : lived.entrySet()) {
				String[] rc = e.getKey().split(",");
				int rx = Integer.parseInt(rc[0].trim());
				int rz = Integer.parseInt(rc[1].trim());
				for (JsonElement idx : e.getValue().getAsJsonArray()) {
					int i = idx.getAsInt();
					int cx = rx * 32 + (i % 32);
					int cz = rz * 32 + (i / 32);
					warm[w++] = GeoChunkKey.of(cx, cz);
				}
			}
			InstantLore.LOGGER.info("geoguessr: warm zone loaded - {} chunks (threshold {}t)",
					warm.length, thresh);
			return new WarmZoneSampler(warm, thresh);
		} catch (Throwable t) {
			InstantLore.LOGGER.error("geoguessr: inhab.json not parsed - random matches disabled", t);
			return null;
		}
	}

	public boolean available() {
		return warm.length > 0;
	}

	public int size() {
		return warm.length;
	}

	public int threshold() {
		return threshold;
	}

	public OptionalLong pick(Random rnd, LongPredicate used) {
		if (warm.length == 0) {
			return OptionalLong.empty();
		}
		for (int tries = 0; tries < 64; tries++) {
			long k = warm[rnd.nextInt(warm.length)];
			if (!used.test(k)) {
				return OptionalLong.of(k);
			}
		}
		int start = rnd.nextInt(warm.length);
		for (int step = 0; step < warm.length; step++) {
			long k = warm[(start + step) % warm.length];
			if (!used.test(k)) {
				return OptionalLong.of(k);
			}
		}
		return OptionalLong.empty();
	}
}
