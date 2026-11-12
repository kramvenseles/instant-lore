package com.rufana.instantlore.client.effect;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;

public final class EffectHandler {
	private static final String DIRECTORY = "effects/";

	private EffectHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("effect.play", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("effect.play without id field");
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();

			if (client.player == null) {
				return;
			}

			Vec3d position = event.data().has("x")
					? new Vec3d(event.number("x", 0), event.number("y", 0), event.number("z", 0))
					: new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());

			play(id, position);
		});
	}

	private static void play(String id, Vec3d position) {
		String path = DIRECTORY + id + ".json";
		String sha256 = LoreAssets.sha256ForPath(path);

		if (sha256 == null) {
			InstantLore.LOGGER.warn("Effect '{}' not in content (expected file {})", id, path);
			return;
		}

		try {
			String json = new String(ContentCache.readAsset(sha256), StandardCharsets.UTF_8);
			EffectPlayer.play(Effect.parse(json), position);
			InstantLore.LOGGER.info("Effect '{}' started at ({}, {}, {})", id,
					String.format("%.1f", position.x),
					String.format("%.1f", position.y),
					String.format("%.1f", position.z));
		} catch (Exception e) {
			InstantLore.LOGGER.error("Effect '{}' failed to start", id, e);
		}
	}
}
