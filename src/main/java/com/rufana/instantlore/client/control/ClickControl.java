package com.rufana.instantlore.client.control;

import com.rufana.instantlore.InstantLore;

import java.util.concurrent.atomic.AtomicInteger;

public final class ClickControl {
	private static final double MAX_HOLD_SECONDS = 30.0;

	private static final Button ATTACK = new Button("левая");
	private static final Button USE = new Button("правая");

	private ClickControl() {
	}

	private static final class Button {
		final String title;

		final AtomicInteger queued = new AtomicInteger();

		volatile long holdUntil;

		final java.util.concurrent.atomic.AtomicBoolean consumedThisTick =
				new java.util.concurrent.atomic.AtomicBoolean();

		Button(String title) {
			this.title = title;
		}

		void reset() {
			queued.set(0);
			holdUntil = 0;
			consumedThisTick.set(false);
		}
	}

	private static Button button(String name) {
		return "right".equals(name) || "use".equals(name) ? USE : ATTACK;
	}

	public static void click(String name, int count) {
		if (PlayerControl.active() == null) {
			InstantLore.LOGGER.warn("Click received but there is no control session - ignoring");
			return;
		}

		Button target = button(name);
		int times = Math.max(1, Math.min(count, 64));

		target.queued.addAndGet(times);
		InstantLore.LOGGER.info("{} button: presses {}", target.title, times);
	}

	public static void hold(String name, double seconds) {
		if (PlayerControl.active() == null) {
			InstantLore.LOGGER.warn("Hold received but there is no control session - ignoring");
			return;
		}

		double duration = Double.isFinite(seconds) && seconds > 0
				? Math.min(seconds, MAX_HOLD_SECONDS)
				: 3.0;

		Button target = button(name);
		target.holdUntil = System.nanoTime() + (long) (duration * 1_000_000_000L);

		InstantLore.LOGGER.info("{} button held for {} s", target.title, duration);
	}

	public static void releaseButton(String name) {
		button(name).reset();
	}

	public static void reset() {
		ATTACK.reset();
		USE.reset();
	}

	public static boolean isHeld(boolean attack) {
		if (PlayerControl.active() == null) {
			return false;
		}

		Button target = attack ? ATTACK : USE;
		return System.nanoTime() < target.holdUntil;
	}

	public static boolean consumeClick(boolean attack) {
		if (PlayerControl.active() == null) {
			return false;
		}

		Button target = attack ? ATTACK : USE;

		if (target.queued.get() <= 0) {
			return false;
		}

		if (!target.consumedThisTick.compareAndSet(false, true)) {
			return false;
		}

		if (attack && !instantlore$attackReady()) {
			target.consumedThisTick.set(false);
			return false;
		}

		return target.queued.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0;
	}

	public static void endTick() {
		ATTACK.consumedThisTick.set(false);
		USE.consumedThisTick.set(false);
	}

	private static boolean instantlore$attackReady() {
		var player = net.minecraft.client.MinecraftClient.getInstance().player;
		return player == null || player.getAttackCooldownProgress(0.0F) >= 1.0F;
	}

	public static boolean isControlled() {
		return PlayerControl.isMoveLocked();
	}
}
