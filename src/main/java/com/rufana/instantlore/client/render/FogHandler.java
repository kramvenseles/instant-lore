package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;

public final class FogHandler {
	private static final int DEFAULT_COLOR = 0xFF7E848C;

	private FogHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("fog.set", event -> LoreFog.set(
				parseColor(event.string("color", null)),
				(float) event.number("start", 0.0),
				(float) event.number("end", 24.0)));

		LoreEventBus.subscribe("fog.clear", event -> LoreFog.clear());
	}

	private static int parseColor(String spec) {
		if (spec == null) {
			return DEFAULT_COLOR;
		}

		String raw = spec.replace("#", "");

		try {
			long parsed = Long.parseLong(raw, 16);
			return raw.length() <= 6 ? (int) (parsed | 0xFF000000L) : (int) parsed;
		} catch (NumberFormatException e) {
			return DEFAULT_COLOR;
		}
	}
}
