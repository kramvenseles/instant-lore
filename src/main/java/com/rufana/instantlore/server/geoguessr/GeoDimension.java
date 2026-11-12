package com.rufana.instantlore.server.geoguessr;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.nio.file.Path;

public final class GeoDimension {

	private GeoDimension() {
	}

	public static final RegistryKey<World> KEY =
			RegistryKey.of(RegistryKeys.WORLD, Identifier.of("rufana", "geoguessr"));

	public static Path sourceBase(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.LEVEL_DAT).getParent()
				.resolve("geoguessr-src").resolve("s1");
	}
}
