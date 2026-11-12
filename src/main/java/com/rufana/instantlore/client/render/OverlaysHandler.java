package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;

public final class OverlaysHandler {
	private OverlaysHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("overlay.suppress", event -> LoreOverlays.set(true));
		LoreEventBus.subscribe("overlay.restore", event -> LoreOverlays.set(false));
	}
}
