package com.rufana.instantlore.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class LorePerms {

	public static final Set<String> ALWAYS_OPEN = Set.of(
			"emit", "versions", "inspect", "probe", "frozen", "perm", "mods");

	public static final Set<String> OWNER_ONLY = Set.of("sources", "devlog", "dev");

	private static final net.minecraft.command.permission.PermissionCheck ADMIN_CHECK =
			net.minecraft.server.command.CommandManager.GAMEMASTERS_CHECK;

	private static final Set<String> KNOWN = ConcurrentHashMap.newKeySet();

	private static final Set<String> GRANTED = ConcurrentHashMap.newKeySet();

	private static final Path STORAGE = FabricLoader.getInstance()
			.getConfigDir().resolve("instantlore").resolve("permissions.json");

	private LorePerms() {
	}

	public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> gate(
			com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> node) {
		String name = node.getLiteral();
		KNOWN.add(name);
		return node.requires(source -> allows(source, name));
	}

	public static boolean allows(ServerCommandSource source, String name) {
		try {
			if (isOwner(source)) {
				return true;
			}
			if (!ADMIN_CHECK.allows(source.getPermissions())) {
				return false;
			}
			if (OWNER_ONLY.contains(name)) {
				return false;
			}
			return ALWAYS_OPEN.contains(name) || GRANTED.contains(name);
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Permission check for /lore {} failed: {}", name, e.toString());
			return false;
		}
	}

	public static boolean isOwner(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		return player == null
				|| InstantLore.TECH_ADMIN.equalsIgnoreCase(player.getName().getString());
	}


	public static List<String> known() {
		return new ArrayList<>(new TreeSet<>(KNOWN));
	}

	public static boolean isKnown(String name) {
		return KNOWN.contains(name);
	}

	public static boolean isGranted(String name) {
		return GRANTED.contains(name);
	}

	public static boolean grant(String name, MinecraftServer server) {
		if (!GRANTED.add(name)) {
			return false;
		}
		save();
		resend(server);
		return true;
	}

	public static boolean revoke(String name, MinecraftServer server) {
		if (!GRANTED.remove(name)) {
			return false;
		}
		save();
		resend(server);
		return true;
	}

	public static int reset(MinecraftServer server) {
		int had = GRANTED.size();
		if (had == 0) {
			return 0;
		}
		GRANTED.clear();
		save();
		resend(server);
		return had;
	}

	private static void resend(MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				server.getPlayerManager().sendCommandTree(player);
			}
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to broadcast command tree: {}", e.toString());
		}
	}


	public static void registerStorage() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
	}

	private static void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(
					Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("permissions.json - expected an array, found: {}", parsed);
				return;
			}
			GRANTED.clear();
			for (JsonElement element : parsed.getAsJsonArray()) {
				String name = element.getAsString();
				if (!KNOWN.contains(name)) {
					InstantLore.LOGGER.warn(
							"permissions.json: grant for unknown command '{}' - kept, but has no effect",
							name);
				}
				GRANTED.add(name);
			}
			InstantLore.LOGGER.info("/lore permissions: commands granted - {}", GRANTED.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read permissions.json: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray array = new JsonArray();
			for (String name : new TreeSet<>(GRANTED)) {
				array.add(name);
			}
			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, array.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save permissions.json: {}", e.toString());
		}
	}
}
