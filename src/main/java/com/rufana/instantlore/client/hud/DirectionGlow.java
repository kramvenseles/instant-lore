package com.rufana.instantlore.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class DirectionGlow {
	private static final double MAX_SECONDS = 15.0;

	private static final int DEFAULT_COLOR = 0xA8F5C77A;

	private static final double EDGE_ANGLE = 75.0;

	private static final int STRIPS = 48;

	private static final double RADIUS_FRACTION = 0.45;

	private static final double PULSE_DEPTH = 0.18;
	private static final double PULSE_HZ = 0.9;

	private static volatile double sourceYaw;
	private static volatile int color = DEFAULT_COLOR;
	private static volatile long startedNanos;
	private static volatile double inSeconds;
	private static volatile double holdSeconds;
	private static volatile double outSeconds;

	private DirectionGlow() {
	}

	public static void glow(double yaw, String colorSpec, double in, double hold, double out) {
		double clampedIn = clampSeconds(in);
		double clampedHold = clampSeconds(hold);
		double clampedOut = clampSeconds(out);

		if (clampedIn + clampedHold + clampedOut <= 0) {
			clear();
			return;
		}

		sourceYaw = yaw;
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

		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null) {
			return;
		}

		double viewYaw = client.gameRenderer != null && client.gameRenderer.getCamera() != null
				? client.gameRenderer.getCamera().getYaw()
				: client.player.getYaw();

		double delta = MathHelper.wrapDegrees(sourceYaw - viewYaw);

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();

		double clamped = MathHelper.clamp(delta, -EDGE_ANGLE, EDGE_ANGLE);
		double centerX = width / 2.0 + (clamped / EDGE_ANGLE) * (width / 2.0);
		double radius = width * RADIUS_FRACTION;

		double pulse = 1.0 - PULSE_DEPTH
				+ PULSE_DEPTH * Math.sin(elapsed * 2.0 * Math.PI * PULSE_HZ);
		double peak = ((color >>> 24) & 0xFF) * envelope(elapsed) * pulse * sideScale(delta);

		if (peak < 1.0) {
			return;
		}

		int rgb = color & 0x00FFFFFF;
		int half = height / 2;

		for (int i = 0; i < STRIPS; i++) {
			int x0 = i * width / STRIPS;
			int x1 = (i + 1) * width / STRIPS;
			double distance = Math.abs((x0 + x1) / 2.0 - centerX) / radius;

			if (distance >= 1.0) {
				continue;
			}

			int alpha = (int) Math.round(peak * Math.pow(1.0 - distance, 1.7));

			if (alpha <= 0) {
				continue;
			}

			int edge = (alpha * 90 / 255) << 24 | rgb;
			int core = (alpha << 24) | rgb;

			context.fillGradient(x0, 0, x1, half, edge, core);
			context.fillGradient(x0, half, x1, height, core, edge);
		}
	}

	private static double envelope(double t) {
		if (t < inSeconds) {
			return inSeconds <= 0 ? 1.0 : t / inSeconds;
		}

		if (t < inSeconds + holdSeconds) {
			return 1.0;
		}

		if (outSeconds <= 0) {
			return 0.0;
		}

		return Math.max(0.0, 1.0 - (t - inSeconds - holdSeconds) / outSeconds);
	}

	private static double sideScale(double delta) {
		return 0.55 + 0.45 * Math.min(1.0, Math.abs(delta) / EDGE_ANGLE);
	}

	private static double clampSeconds(double seconds) {
		if (!Double.isFinite(seconds) || seconds <= 0) {
			return 0.0;
		}

		return Math.min(seconds, MAX_SECONDS);
	}

	private static int parseColor(String spec) {
		try {
			String raw = spec.startsWith("#") ? spec.substring(1) : spec;
			long value = Long.parseLong(raw, 16);

			return raw.length() <= 6 ? (int) (0xFF000000L | value) : (int) value;
		} catch (RuntimeException e) {
			return DEFAULT_COLOR;
		}
	}
}
