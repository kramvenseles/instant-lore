package com.rufana.instantlore.client.ui;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record UiNode(
		String type, String id, String anchor,
		int x, int y, int width, int height,
		int color, int background, int hover,
		String text, double scale, String align,
		String action, boolean enabled,
		String texture, String item, int count,
		int x2, int y2, int thickness,
		String placeholder, int maxLength,
		double srcX, double srcY, int srcW, int srcH, int texW, int texH,
		boolean clip,
		List<UiNode> children
) {
	public boolean cropped() {
		return "image".equals(type) && texW > 0 && texH > 0;
	}

	public boolean clips() {
		return clip;
	}

	public boolean clickable() {
		return action != null && !action.isEmpty();
	}

	public boolean scrollable() {
		return "scroll".equals(type);
	}

	public boolean isButton() {
		return "button".equals(type);
	}

	public static UiNode parse(JsonObject o) {
		int background = color(o, "background", 0x00000000);

		List<UiNode> children = new ArrayList<>();
		if (o.has("children") && o.get("children").isJsonArray()) {
			for (var item : o.getAsJsonArray("children")) {
				if (item.isJsonObject()) {
					children.add(parse(item.getAsJsonObject()));
				}
			}
		}

		return new UiNode(
				string(o, "type", "text"),
				o.has("id") ? o.get("id").getAsString() : null,
				string(o, "anchor", "top_left"),
				integer(o, "x", 0),
				integer(o, "y", 0),
				integer(o, "width", 0),
				integer(o, "height", 0),
				color(o, "color", 0xFFFFFFFF),
				background,
				color(o, "hover", background),
				string(o, "text", ""),
				o.has("scale") ? o.get("scale").getAsDouble() : 1.0,
				string(o, "align", "left"),
				o.has("action") ? o.get("action").getAsString() : null,
				!o.has("enabled") || o.get("enabled").getAsBoolean(),
				o.has("texture") ? o.get("texture").getAsString() : null,
				o.has("item") ? o.get("item").getAsString() : null,
				integer(o, "count", 1),
				integer(o, "x2", 0),
				integer(o, "y2", 0),
				integer(o, "thickness", 1),
				string(o, "placeholder", ""),
				integer(o, "max", 128),
				o.has("src_x") ? o.get("src_x").getAsDouble() : 0.0,
				o.has("src_y") ? o.get("src_y").getAsDouble() : 0.0,
				integer(o, "src_w", 0),
				integer(o, "src_h", 0),
				integer(o, "tex_w", 0),
				integer(o, "tex_h", 0),
				o.has("clip") && o.get("clip").getAsBoolean(),
				children);
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
