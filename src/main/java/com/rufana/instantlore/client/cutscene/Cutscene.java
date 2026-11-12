package com.rufana.instantlore.client.cutscene;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.event.LoreEvent;

import java.util.ArrayList;
import java.util.List;

public record Cutscene(
		double duration,
		boolean lockInput,
		boolean relative,
		boolean hideHud,
		String easing,
		boolean debug,
		List<Keyframe> keyframes,
		List<TimelineEvent> events
) {
	public record Keyframe(double time, double[] camera, double[] lookAt, double roll) {
	}

	public record TimelineEvent(double time, LoreEvent event) {
	}

	public static Cutscene parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();

		List<Keyframe> keyframes = new ArrayList<>();
		if (root.has("keyframes")) {
			for (var element : root.getAsJsonArray("keyframes")) {
				JsonObject frame = element.getAsJsonObject();
				keyframes.add(new Keyframe(
						frame.has("t") ? frame.get("t").getAsDouble() : 0.0,
						triple(frame, "camera"),
						triple(frame, "look_at"),
						frame.has("roll") ? frame.get("roll").getAsDouble() : 0.0));
			}
		}

		keyframes.sort((a, b) -> Double.compare(a.time(), b.time()));

		List<TimelineEvent> events = new ArrayList<>();
		if (root.has("events")) {
			for (var element : root.getAsJsonArray("events")) {
				JsonObject entry = element.getAsJsonObject();

				if (!entry.has("event")) {
					continue;
				}

				double time = entry.has("t") ? entry.get("t").getAsDouble() : 0.0;
				JsonObject data = entry.has("data")
						? entry.getAsJsonObject("data") : new JsonObject();

				events.add(new TimelineEvent(
						time, new LoreEvent(entry.get("event").getAsString(), data)));
			}
		}

		events.sort((a, b) -> Double.compare(a.time(), b.time()));

		return new Cutscene(
				duration(root),
				!root.has("lock_input") || root.get("lock_input").getAsBoolean(),
				root.has("relative") && root.get("relative").getAsBoolean(),
				!root.has("hide_hud") || root.get("hide_hud").getAsBoolean(),
				root.has("easing") ? root.get("easing").getAsString() : "smooth",
				root.has("debug") && root.get("debug").getAsBoolean(),
				keyframes,
				events);
	}

	private static final double MAX_DURATION = 300.0;

	private static final double DEFAULT_DURATION = 5.0;

	private static double duration(JsonObject root) {
		if (!root.has("duration")) {
			return DEFAULT_DURATION;
		}

		double value = root.get("duration").getAsDouble();

		if (!Double.isFinite(value) || value <= 0) {
			return DEFAULT_DURATION;
		}

		return Math.min(value, MAX_DURATION);
	}

	private static double[] triple(JsonObject frame, String key) {
		if (!frame.has(key)) {
			return new double[]{0, 0, 0};
		}

		JsonArray array = frame.getAsJsonArray(key);
		return new double[]{
				array.get(0).getAsDouble(),
				array.get(1).getAsDouble(),
				array.get(2).getAsDouble()};
	}
}
