package com.rufana.instantlore.client.hud;

import net.minecraft.client.gui.DrawContext;

public final class ScreenFade {
	private static final double MAX_SECONDS = 60.0;

	private static final int DEFAULT_COLOR = 0xFF000000;

	private static volatile int color = DEFAULT_COLOR;
	private static volatile long startedNanos;
	private static volatile double inSeconds;
	private static volatile double holdSeconds;
	private static volatile double outSeconds;

	private ScreenFade() {
	}

	public static void fade(String colorSpec, double in, double hold, double out) {
		double clampedIn = clampSeconds(in);
		double clampedHold = clampSeconds(hold);
		double clampedOut = clampSeconds(out);

		if (clampedIn + clampedHold + clampedOut <= 0) {
			clear();
			return;
		}

		color = parseColor(colorSpec);
		inSeconds = clampedIn;
		holdSeconds = clampedHold;
		outSeconds = clampedOut;
		startedNanos = System.nanoTime();
	}

	public static void clear() {
		startedNanos = 0L;
	}

	public static void render(DrawContext context) {
		if (startedNanos == 0L) {
			return;
		}

		double elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
		double total = inSeconds + holdSeconds + outSeconds;

		if (elapsed >= total) {
			startedNanos = 0L;
			return;
		}

		double envelope = envelope(elapsed);
		int baseAlpha = (color >>> 24) & 0xFF;
		int alpha = (int) Math.round(baseAlpha * envelope);

		if (alpha <= 0) {
			return;
		}

		int drawColor = (alpha << 24) | (color & 0x00FFFFFF);
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), drawColor);
	}

	private static double envelope(double t) {
		if (t < inSeconds) {
			return inSeconds <= 0 ? 1.0 : t / inSeconds;
		}

		if (t < inSeconds + holdSeconds) {
			return 1.0;
		}

		double fadeOutStart = inSeconds + holdSeconds;

		if (outSeconds <= 0) {
			return 0.0;
		}

		return Math.max(0.0, 1.0 - (t - fadeOutStart) / outSeconds);
	}

	private static double clampSeconds(double seconds) {
		if (!Double.isFinite(seconds) || seconds <= 0) {
			return 0.0;
		}

		return Math.min(seconds, MAX_SECONDS);
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
