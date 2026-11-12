package com.rufana.instantlore.client.ui;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;
import com.rufana.instantlore.client.text.Placeholders;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ScreenHandler {
	private static final String DIRECTORY = "screen/";

	private ScreenHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("screen.show", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("screen.show without id field");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? Placeholders.flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			show(id, values);
		});

		LoreEventBus.subscribe("screen.close", event -> {
			MinecraftClient client = MinecraftClient.getInstance();

			if (client.currentScreen instanceof LoreScreen) {
				client.setScreen(null);
			}
		});
	}

	private static void show(String id, Map<String, String> values) {
		String path = DIRECTORY + id + ".json";
		String sha256 = LoreAssets.sha256ForPath(path);

		if (sha256 == null) {
			InstantLore.LOGGER.warn("Screen '{}' not in content (expected file {})", id, path);
			return;
		}

		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null) {
				return;
			}
			String json = new String(ContentCache.readAsset(sha256), StandardCharsets.UTF_8);
			UiScreen definition = UiScreen.parse(json);
			client.setScreen(new LoreScreen(id, definition, values));
			InstantLore.LOGGER.info("Screen '{}' shown", id);
		} catch (Exception e) {
			InstantLore.LOGGER.error("Screen '{}' failed to open", id, e);
		}
	}
}
