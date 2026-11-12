package com.rufana.instantlore.client.hud;

public final class CrosshairState {
	public static final int DEFAULT_SIZE = 15;

	public record Crosshair(String anchor, double fromX, double fromY, double toX, double toY,
			long startMs, long durMs, String texture, int size, boolean exiting) {

		public double progress(long now) {
			if (durMs <= 0) {
				return 1.0;
			}
			double p = (now - startMs) / (double) durMs;
			return p < 0.0 ? 0.0 : Math.min(p, 1.0);
		}

		public double curX(long now) {
			return fromX + (toX - fromX) * progress(now);
		}

		public double curY(long now) {
			return fromY + (toY - fromY) * progress(now);
		}

		public boolean finished(long now) {
			return progress(now) >= 1.0;
		}
	}

	private static volatile Crosshair current;

	private static volatile int generation;

	private CrosshairState() {
	}

	public static void set(String anchor, int x, int y, String texture, int size, long durMs) {
		long now = System.currentTimeMillis();
		Crosshair prev = current;

		double fromX = prev == null ? 0.0 : prev.curX(now);
		double fromY = prev == null ? 0.0 : prev.curY(now);

		current = new Crosshair(anchor, fromX, fromY, x, y, now, Math.max(0, durMs),
				texture, size, false);
		generation++;
	}

	public static void hide(long durMs) {
		Crosshair prev = current;

		if (prev == null) {
			return;
		}

		if (durMs <= 0) {
			clear();
			return;
		}

		long now = System.currentTimeMillis();
		current = new Crosshair(prev.anchor(), prev.curX(now), prev.curY(now), 0.0, 0.0,
				now, durMs, prev.texture(), prev.size(), true);
		generation++;
	}

	public static void clear() {
		current = null;
		generation++;
	}

	public static Crosshair current() {
		return current;
	}

	public static boolean active() {
		return current != null;
	}

	public static int generation() {
		return generation;
	}
}
