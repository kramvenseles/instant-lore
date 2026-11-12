package com.rufana.instantlore.client.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record Dialog(String title, String text, List<Option> options) {
	public record Option(String key, String label) {
	}

	public static Dialog parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();

		String title = root.has("title") ? root.get("title").getAsString() : "";
		String text = root.has("text") ? root.get("text").getAsString() : "";

		List<Option> options = new ArrayList<>();

		if (root.has("options")) {
			for (var element : root.getAsJsonArray("options")) {
				JsonObject option = element.getAsJsonObject();

				if (!option.has("key")) {
					continue;
				}

				String key = option.get("key").getAsString();
				String label = option.has("label") ? option.get("label").getAsString() : key;
				options.add(new Option(key, label));
			}
		}

		return new Dialog(title, text, options);
	}
}
