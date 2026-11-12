package com.rufana.instantlore.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record UiScreen(String title, List<UiNode> nodes) {
	public static UiScreen parse(String json) {
		return parse(JsonParser.parseString(json).getAsJsonObject());
	}

	public static UiScreen parse(JsonObject root) {
		String title = root.has("title") ? root.get("title").getAsString() : "";

		List<UiNode> nodes = new ArrayList<>();
		if (root.has("nodes")) {
			for (var item : root.getAsJsonArray("nodes")) {
				nodes.add(UiNode.parse(item.getAsJsonObject()));
			}
		}

		return new UiScreen(title, nodes);
	}
}
