package com.rufana.instantlore.client.render;

import java.util.Set;

public final class ClientTintedBlocks {
	private static volatile Set<Long> tinted = Set.of();

	private static volatile int color = 0x8C8F8A82;

	private ClientTintedBlocks() {
	}

	public static void setAll(Set<Long> positions, int argb) {
		color = argb;
		tinted = Set.copyOf(positions);
	}

	public static Set<Long> snapshot() {
		return tinted;
	}

	public static void clear() {
		tinted = Set.of();
	}

	public static boolean isEmpty() {
		return tinted.isEmpty();
	}

	public static boolean contains(long packed) {
		return tinted.contains(packed);
	}

	public static int color() {
		return color;
	}

	public static float factor() {
		int alpha = (color >>> 24) & 0xFF;
		return 1.0F - alpha / 255.0F;
	}
}
