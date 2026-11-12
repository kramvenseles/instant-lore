package com.rufana.instantlore.client.render;

public final class LoreOverlays {
	private static volatile boolean suppressed = false;

	private LoreOverlays() {
	}

	public static void set(boolean on) {
		suppressed = on;
	}

	public static boolean isSuppressed() {
		return suppressed;
	}
}
