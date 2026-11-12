package com.rufana.instantlore.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.LoreEventPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostRegistry {

	private static final ConcurrentHashMap<UUID, Map<UUID, String>> GHOSTS = new ConcurrentHashMap<>();

	private static final Path STORAGE = net.fabricmc.loader.api.FabricLoader.getInstance()
			.getConfigDir().resolve("instantlore").resolve("ghost.json");

	private static volatile MinecraftServer server;

	private GhostRegistry() {
	}

	public static boolean add(ServerPlayerEntity viewer, ServerPlayerEntity target) {
		Map<UUID, String> targets = GHOSTS.computeIfAbsent(viewer.getUuid(), k -> new ConcurrentHashMap<>());
		if (targets.putIfAbsent(target.getUuid(), target.getName().getString()) != null) {
			return false;
		}
		syncTo(viewer);
		save();
		return true;
	}

	public static boolean remove(UUID viewer, UUID target) {
		Map<UUID, String> targets = GHOSTS.get(viewer);
		if (targets == null || targets.remove(target) == null) {
			return false;
		}
		if (targets.isEmpty()) {
			GHOSTS.remove(viewer);
		}
		ServerPlayerEntity online = playerOf(viewer);
		if (online != null) {
			syncTo(online);
		}
		save();
		return true;
	}

	public static boolean removeByName(UUID viewer, String name) {
		Map<UUID, String> targets = GHOSTS.get(viewer);
		if (targets == null) {
			return false;
		}
		for (Map.Entry<UUID, String> entry : targets.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				return remove(viewer, entry.getKey());
			}
		}
		return false;
	}

	public static List<String> list(UUID viewer) {
		Map<UUID, String> targets = GHOSTS.get(viewer);
		return targets == null ? List.of() : new ArrayList<>(targets.values());
	}

	public static void syncTo(ServerPlayerEntity viewer) {
		try {
			if (!ServerPlayNetworking.canSend(viewer, LoreEventPayload.ID)) {
				return;
			}
			Map<UUID, String> targets = GHOSTS.get(viewer.getUuid());
			JsonArray uuids = new JsonArray();
			if (targets != null) {
				for (UUID id : targets.keySet()) {
					uuids.add(id.toString());
				}
			}
			JsonObject data = new JsonObject();
			data.add("uuids", uuids);
			ServerPlayNetworking.send(viewer, new LoreEventPayload("lore.ghost", data.toString()));
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to sync ghost list to player {}",
					viewer.getName().getString(), e);
		}
	}

	private static ServerPlayerEntity playerOf(UUID uuid) {
		MinecraftServer s = server;
		return s == null ? null : s.getPlayerManager().getPlayer(uuid);
	}


	public static void registerStorage() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> server = null);
	}

	private static void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("ghost.json - expected an array, found: {}", parsed);
				return;
			}
			int pairs = 0;
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject entry = element.getAsJsonObject();
				UUID viewer = UUID.fromString(entry.get("viewer").getAsString());
				Map<UUID, String> targets = new ConcurrentHashMap<>();
				for (JsonElement t : entry.getAsJsonArray("targets")) {
					JsonObject to = t.getAsJsonObject();
					targets.put(UUID.fromString(to.get("uuid").getAsString()), to.get("name").getAsString());
					pairs++;
				}
				if (!targets.isEmpty()) {
					GHOSTS.put(viewer, targets);
				}
			}
			InstantLore.LOGGER.info("Ghosts loaded: {} viewers, {} pairs", GHOSTS.size(), pairs);
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read ghost.json: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray array = new JsonArray();
			for (Map.Entry<UUID, Map<UUID, String>> viewer : GHOSTS.entrySet()) {
				JsonObject entry = new JsonObject();
				entry.addProperty("viewer", viewer.getKey().toString());
				JsonArray targets = new JsonArray();
				for (Map.Entry<UUID, String> t : viewer.getValue().entrySet()) {
					JsonObject to = new JsonObject();
					to.addProperty("uuid", t.getKey().toString());
					to.addProperty("name", t.getValue());
					targets.add(to);
				}
				entry.add("targets", targets);
				array.add(entry);
			}
			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, array.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save ghost.json: {}", e.toString());
		}
	}
}
