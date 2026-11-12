package com.rufana.instantlore.client.camera;

import com.rufana.instantlore.InstantLore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public final class CameraEffects {
	private static final double MAX_SECONDS = 60.0;

	private static final double MAX_AMPLITUDE = 45.0;

	private static volatile double shakeAmplitude;
	private static volatile double shakeFrequency = 20.0;
	private static volatile long shakeUntilNanos;
	private static volatile long shakeStartedNanos;

	private static volatile float fovMultiplier = 1.0F;
	private static volatile long fovUntilNanos;

	private CameraEffects() {
	}


	public static void shake(double amplitude, double frequency, double seconds) {
		if (!Double.isFinite(amplitude) || amplitude <= 0 || !Double.isFinite(seconds)
				|| seconds <= 0) {
			shakeUntilNanos = 0L;
			return;
		}

		shakeAmplitude = Math.min(amplitude, MAX_AMPLITUDE);
		shakeFrequency = Double.isFinite(frequency) && frequency > 0
				? Math.clamp(frequency, 0.5, 60.0)
				: 20.0;

		long now = System.nanoTime();
		shakeStartedNanos = now;
		shakeUntilNanos = now + (long) (Math.min(seconds, MAX_SECONDS) * 1_000_000_000L);
	}

	public static boolean isShaking() {
		return shakeUntilNanos > 0 && System.nanoTime() < shakeUntilNanos;
	}

	public static float[] shakeOffset() {
		long now = System.nanoTime();

		if (shakeUntilNanos <= 0 || now >= shakeUntilNanos) {
			return null;
		}

		double elapsed = (now - shakeStartedNanos) / 1_000_000_000.0;
		double total = (shakeUntilNanos - shakeStartedNanos) / 1_000_000_000.0;
		double fade = total <= 0 ? 0 : Math.max(0.0, 1.0 - elapsed / total);
		double swing = shakeAmplitude * fade;
		double phase = elapsed * shakeFrequency * 2 * Math.PI;

		return new float[]{
				(float) (Math.sin(phase) * swing),
				(float) (Math.sin(phase * 1.37) * swing)};
	}


	public static void fov(double multiplier, double seconds) {
		if (!Double.isFinite(multiplier) || multiplier <= 0) {
			fovMultiplier = 1.0F;
			fovUntilNanos = 0L;
			return;
		}

		fovMultiplier = (float) Math.clamp(multiplier, 0.1, 4.0);
		fovUntilNanos = Double.isFinite(seconds) && seconds > 0
				? System.nanoTime() + (long) (Math.min(seconds, MAX_SECONDS) * 1_000_000_000L)
				: 0L;
	}

	public static float fovMultiplier() {
		if (fovUntilNanos > 0 && System.nanoTime() >= fovUntilNanos) {
			fovMultiplier = 1.0F;
			fovUntilNanos = 0L;
		}

		return fovMultiplier;
	}


	public static void perspective(String mode) {
		Perspective value = switch (mode == null ? "" : mode) {
			case "third", "third_back" -> Perspective.THIRD_PERSON_BACK;
			case "third_front" -> Perspective.THIRD_PERSON_FRONT;
			case "first" -> Perspective.FIRST_PERSON;
			default -> {
				InstantLore.LOGGER.warn("Unknown camera view '{}', using first person", mode);
				yield Perspective.FIRST_PERSON;
			}
		};

		MinecraftClient.getInstance().options.setPerspective(value);
	}
}
