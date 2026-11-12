package com.rufana.instantlore.client.hud;

import com.rufana.instantlore.InstantLore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HudState {
	private static final Map<String, HudLayout> SHOWN = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> VALUES = new ConcurrentHashMap<>();

	private static volatile int generation;

	private HudState() {
	}

	public static void show(String id, HudLayout layout) {
		SHOWN.put(id, layout);
		VALUES.putIfAbsent(id, new ConcurrentHashMap<>());
		generation++;
	}

	public static void hide(String id) {
		SHOWN.remove(id);
		VALUES.remove(id);
		generation++;
	}

	public static void hideAll() {
		SHOWN.clear();
		VALUES.clear();
		generation++;
	}

	private static final int MAX_TRACKED = 64;

	public static void setValues(String id, Map<String, String> values) {
		if (!VALUES.containsKey(id) && VALUES.size() >= MAX_TRACKED) {
			InstantLore.LOGGER.warn("Already {} HUD values - set '{}' ignored",
					VALUES.size(), id);
			return;
		}

		VALUES.computeIfAbsent(id, key -> new ConcurrentHashMap<>()).putAll(values);
		generation++;
	}

	public static int generation() {
		return generation;
	}

	public static Map<String, HudLayout> shown() {
		return new LinkedHashMap<>(SHOWN);
	}

	public static Map<String, String> values(String id) {
		return VALUES.getOrDefault(id, Map.of());
	}
}
