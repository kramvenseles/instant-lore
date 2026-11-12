package com.rufana.instantlore.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.SoulMoneyPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoulMoney {

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("soulmoney.json");

	private static final Map<UUID, Long> BALANCE = new ConcurrentHashMap<>();

	private SoulMoney() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
	}

	public static long get(UUID uuid) {
		Long v = BALANCE.get(uuid);
		return v == null ? 0L : v;
	}

	public static void syncTo(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, SoulMoneyPayload.ID)) {
			ServerPlayNetworking.send(player, new SoulMoneyPayload(get(player.getUuid())));
		}
	}

	public static synchronized long deposit(UUID uuid, long amount) {
		if (amount <= 0) {
			return get(uuid);
		}
		long next = get(uuid) + amount;
		BALANCE.put(uuid, next);
		save();
		return next;
	}

	public static synchronized boolean withdraw(UUID uuid, long amount) {
		if (amount <= 0) {
			return true;
		}
		long have = get(uuid);
		if (have < amount) {
			return false;
		}
		long next = have - amount;
		if (next == 0) {
			BALANCE.remove(uuid);
		} else {
			BALANCE.put(uuid, next);
		}
		save();
		return true;
	}

	public static synchronized void set(UUID uuid, long amount) {
		if (amount <= 0) {
			BALANCE.remove(uuid);
		} else {
			BALANCE.put(uuid, amount);
		}
		save();
	}


	private static void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("soulmoney.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject entry = element.getAsJsonObject();
				long value = entry.get("balance").getAsLong();
				if (value > 0) {
					BALANCE.put(UUID.fromString(entry.get("uuid").getAsString()), value);
				}
			}
			InstantLore.LOGGER.info("Soul coin balances loaded: {}", BALANCE.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read soulmoney.json: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray array = new JsonArray();
			for (Map.Entry<UUID, Long> entry : BALANCE.entrySet()) {
				JsonObject object = new JsonObject();
				object.addProperty("uuid", entry.getKey().toString());
				object.addProperty("balance", entry.getValue());
				array.add(object);
			}
			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, array.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save soulmoney.json: {}", e.toString());
		}
	}
}
