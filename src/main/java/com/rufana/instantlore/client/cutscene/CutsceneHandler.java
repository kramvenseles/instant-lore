package com.rufana.instantlore.client.cutscene;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;

public final class CutsceneHandler {
	private static final String DIRECTORY = "cutscenes/";

	private CutsceneHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("cutscene.play", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("cutscene.play without id field");
				return;
			}

			boolean hasX = event.data().has("x");
			boolean hasY = event.data().has("y");
			boolean hasZ = event.data().has("z");
			Vec3d anchor = null;

			if (hasX && hasY && hasZ) {
				anchor = new Vec3d(event.number("x", 0), event.number("y", 0), event.number("z", 0));
			} else if (hasX || hasY || hasZ) {
				InstantLore.LOGGER.warn(
						"cutscene.play '{}': anchor partially set (x={}, y={}, z={}) - playing without it",
						id, hasX, hasY, hasZ);
			}

			Float yaw = event.data().has("yaw") ? (float) event.number("yaw", 0) : null;

			play(id, anchor, yaw);
		});

		LoreEventBus.subscribe("cutscene.stop", event -> CutscenePlayer.stop());
	}

	private static void play(String id, Vec3d anchor, Float yaw) {
		String path = DIRECTORY + id + ".json";
		String sha256 = LoreAssets.sha256ForPath(path);

		if (sha256 == null) {
			InstantLore.LOGGER.warn("Cutscene '{}' not in content (expected file {})", id, path);
			return;
		}

		try {
			String json = new String(ContentCache.readAsset(sha256), StandardCharsets.UTF_8);
			CutscenePlayer.play(Cutscene.parse(json), anchor, yaw);
		} catch (Exception e) {
			InstantLore.LOGGER.error("Cutscene '{}' failed to start", id, e);
		}
	}
}
