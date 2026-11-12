package com.rufana.instantlore.content;

public final class RideTurn {
	private static final long LIFETIME_NANOS = 60_000_000L;

	private static volatile int passengerId = -1;
	private static volatile float delta;
	private static volatile long recordedAt;

	private RideTurn() {
	}

	public static void record(int id, float degrees) {
		passengerId = id;
		delta = degrees;
		recordedAt = System.nanoTime();
	}

	public static float backlog(int id, float tickDelta) {
		if (id != passengerId || System.nanoTime() - recordedAt > LIFETIME_NANOS) {
			return 0.0F;
		}

		return delta * (1.0F - tickDelta);
	}
}
