package com.rufana.instantlore.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record Effect(double duration, List<Emitter> emitters, Sound sound) {
	public record Emitter(
			String particle, String shape,
			double radius, double height, double turns,
			double[] size, double[] to,
			double rate,
			double[] offset, double[] velocity, double spread
	) {
	}

	public record Sound(String id, float volume, float pitch) {
	}

	public static Effect parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();

		List<Emitter> emitters = new ArrayList<>();
		if (root.has("emitters")) {
			for (var item : root.getAsJsonArray("emitters")) {
				JsonObject e = item.getAsJsonObject();
				emitters.add(new Emitter(
						e.has("particle") ? e.get("particle").getAsString() : "minecraft:flame",
						e.has("shape") ? e.get("shape").getAsString() : "point",
						number(e, "radius", 1.0),
						number(e, "height", 1.0),
						number(e, "turns", 2.0),
						triple(e, "size", 0.5, 0.5, 0.5),
						triple(e, "to", 0, 1, 0),
						number(e, "rate", 20.0),
						triple(e, "offset", 0, 0, 0),
						triple(e, "velocity", 0, 0, 0),
						number(e, "spread", 0.0)));
			}
		}

		Sound sound = null;
		if (root.has("sound")) {
			JsonObject s = root.getAsJsonObject("sound");
			sound = new Sound(
					s.get("id").getAsString(),
					s.has("volume") ? s.get("volume").getAsFloat() : 1.0F,
					s.has("pitch") ? s.get("pitch").getAsFloat() : 1.0F);
		}

		return new Effect(duration(root), emitters, sound);
	}

	private static double duration(JsonObject root) {
		if (!root.has("duration")) {
			return 1.0;
		}

		double value = root.get("duration").getAsDouble();
		return Double.isFinite(value) && value > 0 ? Math.min(value, 30.0) : 1.0;
	}

	private static double number(JsonObject o, String key, double fallback) {
		return o.has(key) ? o.get(key).getAsDouble() : fallback;
	}

	private static double[] triple(JsonObject o, String key, double x, double y, double z) {
		double[] result = {x, y, z};

		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return result;
		}

		JsonArray a = o.getAsJsonArray(key);

		for (int i = 0; i < 3 && i < a.size(); i++) {
			result[i] = a.get(i).getAsDouble();
		}

		return result;
	}
}
