package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;

public final class CoordsHandler {
	private CoordsHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("coords.scramble", event -> LoreCoords.set(true));
		LoreEventBus.subscribe("coords.clear", event -> LoreCoords.set(false));
	}
}
