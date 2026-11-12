package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;

public final class LoreSky {
	private static volatile boolean hidden = false;

	private LoreSky() {
	}

	public static void register() {
		LoreEventBus.subscribe("sky.hide", event -> hidden = true);
		LoreEventBus.subscribe("sky.show", event -> hidden = false);
	}

	public static boolean isHidden() {
		return hidden;
	}

	public static void show() {
		hidden = false;
	}
}
