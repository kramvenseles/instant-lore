package com.rufana.instantlore.client.script;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.concurrent.CopyOnWriteArrayList;

public final class LoreTimers {
	private static final int MAX_TIMERS = 256;

	private static final long MIN_PERIOD_NANOS = 50_000_000L;

	private static final double MAX_SECONDS = 3600.0;

	private static final CopyOnWriteArrayList<Timer> TIMERS = new CopyOnWriteArrayList<>();

	private LoreTimers() {
	}

	private static final class Timer {
		final LuaFunction function;
		final long periodNanos;
		long dueAt;
		volatile boolean cancelled;

		Timer(LuaFunction function, long periodNanos, long dueAt) {
			this.function = function;
			this.periodNanos = periodNanos;
			this.dueAt = dueAt;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	public static void reset() {
		TIMERS.clear();
	}

	public static LuaValue schedule(double seconds, LuaFunction function, boolean repeat) {
		if (TIMERS.size() >= MAX_TIMERS) {
			throw new LuaError("слишком много таймеров (> " + MAX_TIMERS + ")");
		}

		long period = toNanos(seconds);
		Timer timer = new Timer(function, repeat ? period : 0, System.nanoTime() + period);
		TIMERS.add(timer);

		return new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				timer.cancelled = true;
				return LuaValue.NIL;
			}
		};
	}

	private static long toNanos(double seconds) {
		if (!Double.isFinite(seconds) || seconds < 0) {
			return MIN_PERIOD_NANOS;
		}

		double capped = Math.min(seconds, MAX_SECONDS);
		return Math.max((long) (capped * 1_000_000_000L), MIN_PERIOD_NANOS);
	}

	private static void tick() {
		if (TIMERS.isEmpty()) {
			return;
		}

		long now = System.nanoTime();

		for (Timer timer : TIMERS) {
			if (timer.cancelled) {
				TIMERS.remove(timer);
				continue;
			}

			if (now < timer.dueAt) {
				continue;
			}

			fire(timer);

			if (timer.periodNanos == 0 || timer.cancelled) {
				TIMERS.remove(timer);
			} else {
				timer.dueAt = now + timer.periodNanos;
			}
		}
	}

	private static void fire(Timer timer) {
		try {
			LoreScripts.openBudget();
			timer.function.call();
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Script timer failed, removed", e);
			timer.cancelled = true;
		}
	}
}
