package com.rufana.instantlore.client.control;

import com.rufana.instantlore.InstantLore;

import java.util.Collection;
import java.util.Set;

public final class KeyGate {
	private static final double MAX_SECONDS = 300.0;
	private static final double DEFAULT_SECONDS = 30.0;

	private static volatile Set<String> blocked = Set.of();
	private static volatile long untilNanos;

	private static volatile boolean inverted;

	private KeyGate() {
	}

	public static void block(Collection<String> ids, double seconds) {
		apply(ids, false, seconds);
	}

	public static void blockAllExcept(Collection<String> allowed, double seconds) {
		apply(allowed == null ? Set.of() : allowed, true, seconds);
	}

	private static void apply(Collection<String> ids, boolean invert, double seconds) {
		if (!invert && (ids == null || ids.isEmpty())) {
			clear();
			return;
		}

		double held = Double.isFinite(seconds) && seconds > 0
				? Math.min(seconds, MAX_SECONDS)
				: DEFAULT_SECONDS;

		blocked = Set.copyOf(ids);
		inverted = invert;
		untilNanos = System.nanoTime() + (long) (held * 1_000_000_000L);

		InstantLore.LOGGER.info(invert
						? "Keys locked for {} s, EXCEPT: {}"
						: "Keys locked for {} s: {}",
				held, blocked);
	}

	public static void clear() {
		if (!blocked.isEmpty() || inverted) {
			InstantLore.LOGGER.info("Key lock released");
		}

		blocked = Set.of();
		inverted = false;
		untilNanos = 0L;
	}

	public static boolean isBlocked(String id) {
		Set<String> current = blocked;

		if (current.isEmpty() && !inverted) {
			return false;
		}

		if (System.nanoTime() >= untilNanos) {
			clear();
			return false;
		}

		return inverted != current.contains(id);
	}
}
