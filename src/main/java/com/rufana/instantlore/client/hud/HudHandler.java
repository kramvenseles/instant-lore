package com.rufana.instantlore.client.hud;

import com.google.gson.JsonElement;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class HudHandler {
	private static final String DIRECTORY = "hud/";

	private HudHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("hud.show", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("hud.show without id field");
				return;
			}

			show(id);

			if (event.data().has("values")) {
				HudState.setValues(id, flatten(event.data().getAsJsonObject("values")));
			}
		});

		LoreEventBus.subscribe("hud.hide", event -> {
			String id = event.string("id", null);

			if (id == null) {
				HudState.hideAll();
			} else {
				HudState.hide(id);
			}
		});

		LoreEventBus.subscribe("hud.set", event -> {
			String id = event.string("id", null);

			if (id == null || !event.data().has("values")) {
				return;
			}

			HudState.setValues(id, flatten(event.data().getAsJsonObject("values")));
		});
	}

	private static void show(String id) {
		String path = DIRECTORY + id + ".json";
		String sha256 = LoreAssets.sha256ForPath(path);

		if (sha256 == null) {
			InstantLore.LOGGER.warn("HUD layout '{}' not in content (expected file {})", id, path);
			return;
		}

		try {
			String json = new String(ContentCache.readAsset(sha256), StandardCharsets.UTF_8);
			HudState.show(id, HudLayout.parse(json));
			InstantLore.LOGGER.info("HUD '{}' shown", id);
		} catch (Exception e) {
			InstantLore.LOGGER.error("HUD '{}' failed to parse", id, e);
		}
	}

	private static Map<String, String> flatten(com.google.gson.JsonObject object) {
		Map<String, String> values = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement value = entry.getValue();

			if (value.isJsonPrimitive()) {
				values.put(entry.getKey(), value.getAsString());
			}
		}

		return values;
	}
}
