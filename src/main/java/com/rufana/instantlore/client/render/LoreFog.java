package com.rufana.instantlore.client.render;

public final class LoreFog {
	private static volatile boolean active = false;
	private static volatile int color = 0xFF7E848C;
	private static volatile float start = 0.0F;
	private static volatile float end = 24.0F;

	private LoreFog() {
	}

	public static void set(int argb, float startBlocks, float endBlocks) {
		color = argb;
		start = Math.max(0.0F, startBlocks);
		end = Math.max(start + 0.1F, endBlocks);
		active = true;
	}

	public static void clear() {
		active = false;
	}

	public static boolean isActive() {
		return active;
	}

	public static int color() {
		return color;
	}

	public static float start() {
		return start;
	}

	public static float end() {
		return end;
	}
}
