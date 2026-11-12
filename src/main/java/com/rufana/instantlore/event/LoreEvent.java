package com.rufana.instantlore.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record LoreEvent(String type, JsonObject data) {
	public static LoreEvent of(String type) {
		return new LoreEvent(type, new JsonObject());
	}

	public static LoreEvent parse(String type, String json) {
		JsonObject data = (json == null || json.isBlank())
				? new JsonObject()
				: JsonParser.parseString(json).getAsJsonObject();
		return new LoreEvent(type, data);
	}

	public String string(String key, String fallback) {
		return data.has(key) ? data.get(key).getAsString() : fallback;
	}

	public int integer(String key, int fallback) {
		return data.has(key) ? data.get(key).getAsInt() : fallback;
	}

	public double number(String key, double fallback) {
		return data.has(key) ? data.get(key).getAsDouble() : fallback;
	}

	public boolean bool(String key, boolean fallback) {
		return data.has(key) ? data.get(key).getAsBoolean() : fallback;
	}
}
