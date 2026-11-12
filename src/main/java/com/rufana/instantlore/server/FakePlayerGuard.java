package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class FakePlayerGuard {

	private static final String CARPET_FAKE_CLASS = "carpet.patches.EntityPlayerMPFake";

	public static boolean isCarpetFake(Object player) {
		for (Class<?> c = player.getClass(); c != null; c = c.getSuperclass()) {
			if (c.getName().equals(CARPET_FAKE_CLASS)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasRealPlayerData(MinecraftServer server, String username) {
		if (username == null || username.isEmpty()) {
			return false;
		}
		Path dir = server.getSavePath(WorldSavePath.PLAYERDATA);
		Set<UUID> ids = new LinkedHashSet<>();
		try {
			server.getApiServices().nameToIdCache().findByName(username).ifPresent(p -> ids.add(p.id()));
		} catch (Throwable ignored) {
		}
		ids.add(Uuids.getOfflinePlayerUuid(username));
		for (UUID id : ids) {
			if (Files.exists(dir.resolve(id + ".dat"))) {
				return true;
			}
		}
		return false;
	}

	public static void logBlockedSave(ServerPlayerEntity player) {
		InstantLore.LOGGER.warn("[fpguard] skipped saving playerdata of fake player '{}' - real file protected",
				player.getGameProfile().name());
	}

	public static void logRefusedSpawn(String username) {
		InstantLore.LOGGER.warn("[fpguard] refusing to spawn /player '{}' - a real playerdata exists for this name "
				+ "player. Use a unique bot name.", username);
	}

	private FakePlayerGuard() {
	}
}
