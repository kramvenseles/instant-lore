package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;

public final class LoreCamera {
	private static final float MAX_DISTANCE = 12.0F;

	private static volatile float distance = 0.0F;

	private LoreCamera() {
	}

	public static void register() {
		LoreEventBus.subscribe("camera.distance", event -> {
			if (event.bool("clear", false)) {
				distance = 0.0F;
				return;
			}

			float blocks = (float) event.number("blocks", 0);
			distance = blocks <= 0 ? 0.0F : Math.min(blocks, MAX_DISTANCE);
		});
	}

	public static float distance() {
		return distance;
	}
}
