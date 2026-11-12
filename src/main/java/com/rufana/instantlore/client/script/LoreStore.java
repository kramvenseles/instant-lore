package com.rufana.instantlore.client.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LoreStore {
	private static final Path FILE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("store.json");

	private static final int MAX_KEYS = 256;
	private static final int MAX_VALUE_BYTES = 16 * 1024;

	private static JsonObject data;

	private LoreStore() {
	}

	private static JsonObject data() {
		if (data == null) {
			data = read();
		}
		return data;
	}

	private static JsonObject read() {
		try {
			if (Files.exists(FILE)) {
				JsonElement parsed = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
				if (parsed.isJsonObject()) {
					return parsed.getAsJsonObject();
				}
			}
		} catch (Exception e) {
			InstantLore.LOGGER.warn("store.json not read, starting empty", e);
		}
		return new JsonObject();
	}

	public static JsonElement get(String key) {
		JsonElement value = data().get(key);
		return value == null || value.isJsonNull() ? null : value;
	}

	public static void put(String key, JsonElement value) {
		JsonObject store = data();

		if (value == null || value.isJsonNull()) {
			store.remove(key);
			write(store);
			return;
		}

		if (value.toString().getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
			InstantLore.LOGGER.warn("lore.save('{}'): value larger than {} bytes - dropped", key, MAX_VALUE_BYTES);
			return;
		}

		if (!store.has(key) && store.size() >= MAX_KEYS) {
			InstantLore.LOGGER.warn("lore.save('{}'): key cap of {} reached - dropped", key, MAX_KEYS);
			return;
		}

		store.add(key, value);
		write(store);
	}

	private static void write(JsonObject store) {
		try {
			Files.createDirectories(FILE.getParent());
			Path temp = Files.createTempFile(FILE.getParent(), "store", ".part");
			Files.writeString(temp, store.toString(), StandardCharsets.UTF_8);
			Files.move(temp, FILE, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			InstantLore.LOGGER.error("store.json not written", e);
		}
	}
}
