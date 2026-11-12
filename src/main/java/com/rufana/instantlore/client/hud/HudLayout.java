package com.rufana.instantlore.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record HudLayout(List<Element> elements) {
	public record Element(
			String type, String anchor,
			int x, int y, int width, int height,
			String text, String value,
			int color, int background,
			double scale, String align,
			String texture, String item, int count,
			int x2, int y2, int thickness
	) {
	}

	public static HudLayout parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<Element> elements = new ArrayList<>();

		if (root.has("elements")) {
			for (var item : root.getAsJsonArray("elements")) {
				JsonObject e = item.getAsJsonObject();
				elements.add(new Element(
						string(e, "type", "text"),
						string(e, "anchor", "top_left"),
						integer(e, "x", 0),
						integer(e, "y", 0),
						integer(e, "width", 100),
						integer(e, "height", 6),
						string(e, "text", ""),
						string(e, "value", "0"),
						color(e, "color", 0xFFFFFFFF),
						color(e, "background", 0x80000000),
						e.has("scale") ? e.get("scale").getAsDouble() : 1.0,
						string(e, "align", "left"),
						string(e, "texture", ""),
						string(e, "item", ""),
						integer(e, "count", 1),
						integer(e, "x2", 0),
						integer(e, "y2", 0),
						integer(e, "thickness", 1)));
			}
		}

		return new HudLayout(elements);
	}

	private static String string(JsonObject o, String key, String fallback) {
		return o.has(key) ? o.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject o, String key, int fallback) {
		return o.has(key) ? o.get(key).getAsInt() : fallback;
	}

	private static int color(JsonObject o, String key, int fallback) {
		if (!o.has(key)) {
			return fallback;
		}

		String raw = o.get(key).getAsString().replace("#", "");

		try {
			long parsed = Long.parseLong(raw, 16);
			return raw.length() <= 6 ? (int) (parsed | 0xFF000000L) : (int) parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
