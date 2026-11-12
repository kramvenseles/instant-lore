package com.rufana.instantlore.client.dialog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;
import com.rufana.instantlore.client.text.Placeholders;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DialogHandler {
	private static final String DIRECTORY = "dialog/";

	private DialogHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("dialog.show", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("dialog.show without id field");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			show(id, values);
		});

		LoreEventBus.subscribe("dialog.close", event -> {
			MinecraftClient client = MinecraftClient.getInstance();

			if (client.currentScreen instanceof DialogScreen) {
				client.setScreen(null);
			}
		});
	}

	private static void show(String id, Map<String, String> values) {
		String path = DIRECTORY + id + ".json";
		String sha256 = LoreAssets.sha256ForPath(path);

		if (sha256 == null) {
			InstantLore.LOGGER.warn("Dialog '{}' not in content (expected file {})", id, path);
			return;
		}

		try {
			String json = new String(ContentCache.readAsset(sha256), StandardCharsets.UTF_8);
			Dialog dialog = substitute(Dialog.parse(json), values);
			MinecraftClient.getInstance().setScreen(new DialogScreen(id, dialog));
			InstantLore.LOGGER.info("Dialog '{}' shown", id);
		} catch (Exception e) {
			InstantLore.LOGGER.error("Dialog '{}' failed to open", id, e);
		}
	}

	private static Dialog substitute(Dialog dialog, Map<String, String> values) {
		List<Dialog.Option> options = new ArrayList<>();

		for (Dialog.Option option : dialog.options()) {
			options.add(new Dialog.Option(option.key(), Placeholders.substitute(option.label(), values)));
		}

		return new Dialog(
				Placeholders.substitute(dialog.title(), values),
				Placeholders.substitute(dialog.text(), values),
				options);
	}

	private static Map<String, String> flatten(JsonObject object) {
		Map<String, String> values = new HashMap<>();

		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				values.put(entry.getKey(), entry.getValue().getAsString());
			}
		}

		return values;
	}
}
