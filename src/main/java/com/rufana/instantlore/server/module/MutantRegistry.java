package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreDevLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MutantRegistry {

	private MutantRegistry() {
	}

	private static final class Entry {
		volatile String name;
		final Set<String> kinds = ConcurrentHashMap.newKeySet();

		Entry(String name) {
			this.name = name;
		}
	}

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("mutants.json");

	private static final Path LEGACY_VAMPIRES = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("vampires.json");

	private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

	private static final Map<String, String> KINDS = new ConcurrentHashMap<>();


	public static void registerKind(String kind, String title) {
		KINDS.put(kind, title);
	}

	public static Set<String> knownKinds() {
		return new TreeSet<>(KINDS.keySet());
	}

	public static boolean isKnownKind(String kind) {
		return KINDS.containsKey(kind);
	}

	public static String kindTitle(String kind) {
		return KINDS.getOrDefault(kind, kind);
	}


	public static boolean add(ServerPlayerEntity player, String kind) {
		UUID uuid = player.getUuid();
		String name = player.getName().getString();
		Entry entry = ENTRIES.computeIfAbsent(uuid, u -> new Entry(name));
		entry.name = name;
		boolean added = entry.kinds.add(kind);
		if (added) {
			save();
			LoreDevLog.info("Mutant: {} ({}) +{}", name, uuid, kind);
		}
		return added;
	}

	public static String remove(String name, String kind) {
		for (Map.Entry<UUID, Entry> e : ENTRIES.entrySet()) {
			Entry entry = e.getValue();
			if (entry.name.equalsIgnoreCase(name) && entry.kinds.remove(kind)) {
				if (entry.kinds.isEmpty()) {
					ENTRIES.remove(e.getKey());
				}
				save();
				LoreDevLog.info("Mutant: {} ({}) -{}", entry.name, e.getKey(), kind);
				return entry.name;
			}
		}
		return null;
	}

	public static boolean isKind(UUID uuid, String kind) {
		Entry entry = ENTRIES.get(uuid);
		return entry != null && entry.kinds.contains(kind);
	}

	public static List<UUID> uuidsWithKind(String kind) {
		List<UUID> out = new ArrayList<>();
		for (Map.Entry<UUID, Entry> e : ENTRIES.entrySet()) {
			if (e.getValue().kinds.contains(kind)) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	public static List<String> namesWithKind(String kind) {
		List<String> out = new ArrayList<>();
		for (Entry entry : ENTRIES.values()) {
			if (entry.kinds.contains(kind)) {
				out.add(entry.name);
			}
		}
		return out;
	}

	public static Set<String> kindsOf(String name) {
		for (Entry entry : ENTRIES.values()) {
			if (entry.name.equalsIgnoreCase(name)) {
				return new LinkedHashSet<>(entry.kinds);
			}
		}
		return null;
	}

	public static Map<String, Set<String>> all() {
		Map<String, Set<String>> out = new LinkedHashMap<>();
		ENTRIES.values().stream()
				.sorted((a, b) -> a.name.compareToIgnoreCase(b.name))
				.forEach(entry -> out.put(entry.name, new LinkedHashSet<>(entry.kinds)));
		return out;
	}


	public static void registerStorage() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
	}

	private static void load() {
		if (Files.exists(STORAGE)) {
			readFrom(STORAGE);
			return;
		}
		if (Files.exists(LEGACY_VAMPIRES)) {
			migrateLegacyVampires();
		}
	}

	private static void readFrom(Path path) {
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("mutants.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject obj = element.getAsJsonObject();
				UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
				Entry entry = new Entry(obj.get("name").getAsString());
				JsonArray kinds = obj.getAsJsonArray("kinds");
				if (kinds != null) {
					for (JsonElement k : kinds) {
						entry.kinds.add(k.getAsString());
					}
				}
				if (!entry.kinds.isEmpty()) {
					ENTRIES.put(uuid, entry);
				}
			}
			InstantLore.LOGGER.info("Mutants loaded: {}", ENTRIES.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read mutants.json: {}", e.toString());
		}
	}

	private static void migrateLegacyVampires() {
		try {
			JsonElement parsed = JsonParser.parseString(
					Files.readString(LEGACY_VAMPIRES, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("vampires.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject obj = element.getAsJsonObject();
				UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
				Entry entry = ENTRIES.computeIfAbsent(uuid, u -> new Entry(obj.get("name").getAsString()));
				entry.kinds.add("vampire");
			}
			InstantLore.LOGGER.info("Migration vampires.json → mutants.json: migrated {}", ENTRIES.size());
			if (!ENTRIES.isEmpty()) {
				save();
			}
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to migrate vampires.json: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray array = new JsonArray();
			for (Map.Entry<UUID, Entry> e : ENTRIES.entrySet()) {
				JsonObject obj = new JsonObject();
				obj.addProperty("uuid", e.getKey().toString());
				obj.addProperty("name", e.getValue().name);
				JsonArray kinds = new JsonArray();
				for (String kind : e.getValue().kinds) {
					kinds.add(kind);
				}
				obj.add("kinds", kinds);
				array.add(obj);
			}
			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, array.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save mutants.json: {}", e.toString());
		}
	}
}
