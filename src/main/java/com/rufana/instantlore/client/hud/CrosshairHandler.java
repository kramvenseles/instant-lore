package com.rufana.instantlore.client.hud;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;

public final class CrosshairHandler {
	private CrosshairHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("crosshair.set", event -> {
			String anchor = event.string("anchor", "center");
			int x = event.integer("x", 0);
			int y = event.integer("y", 0);
			int size = event.integer("size", CrosshairState.DEFAULT_SIZE);
			long ms = event.integer("ms", 0);

			String texture = event.string("texture", null);
			if (texture != null && texture.isEmpty()) {
				texture = null;
			}

			CrosshairState.set(anchor, x, y, texture, size, ms);
			InstantLore.LOGGER.info("Crosshair: moving to ({}, {}) in {} ms, anchor={} texture={} size={}",
					x, y, ms, anchor, texture == null ? "(vanilla)" : texture, size);
		});

		LoreEventBus.subscribe("crosshair.hide", event -> {
			long ms = event.integer("ms", 0);
			CrosshairState.hide(ms);
			InstantLore.LOGGER.info("Crosshair: vanilla restored in {} ms", ms);
		});
	}
}
