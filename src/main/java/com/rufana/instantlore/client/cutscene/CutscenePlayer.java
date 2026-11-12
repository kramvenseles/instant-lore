package com.rufana.instantlore.client.cutscene;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class CutscenePlayer {
	private static Cutscene active;
	private static long startedAtNanos;
	private static Vec3d origin = Vec3d.ZERO;

	private static float originYaw;
	private static int lastLoggedSecond;

	private static int nextEventIndex;

	private static float savedYaw;
	private static float savedPitch;

	private static boolean savedHudHidden;

	private CutscenePlayer() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	private static void tick() {
		Cutscene cutscene = active;

		if (cutscene == null) {
			return;
		}

		if (elapsedSeconds() >= cutscene.duration()) {
			stop();
			return;
		}

		fireDueEvents(cutscene);
	}

	private static void fireDueEvents(Cutscene cutscene) {
		double t = elapsedSeconds();
		var events = cutscene.events();

		while (nextEventIndex < events.size() && events.get(nextEventIndex).time() <= t) {
			Cutscene.TimelineEvent event = events.get(nextEventIndex);
			nextEventIndex++;

			LoreEventBus.publish(event.event());

			if (active != cutscene) {
				return;
			}
		}
	}

	public static boolean isActive() {
		expireIfOver();
		return active != null;
	}

	public static boolean isInputLocked() {
		expireIfOver();
		return active != null && active.lockInput();
	}

	private static void expireIfOver() {
		Cutscene cutscene = active;

		if (cutscene != null && elapsedSeconds() >= cutscene.duration()) {
			stop();
		}
	}

	public static void play(Cutscene cutscene) {
		play(cutscene, null, null);
	}

	public static void play(Cutscene cutscene, Vec3d anchor, Float yaw) {
		if (cutscene.keyframes().size() < 2) {
			InstantLore.LOGGER.warn("Cutscene needs at least two frames, got {}",
					cutscene.keyframes().size());
			return;
		}

		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null) {
			return;
		}

		active = cutscene;
		startedAtNanos = System.nanoTime();
		lastLoggedSecond = -1;
		nextEventIndex = 0;
		origin = anchor != null
				? anchor
				: cutscene.relative()
						? new Vec3d(player.getX(), player.getY(), player.getZ())
						: Vec3d.ZERO;

		originYaw = yaw == null ? 0.0F : yaw;

		savedYaw = player.getYaw();
		savedPitch = player.getPitch();

		savedHudHidden = MinecraftClient.getInstance().options.hudHidden;

		if (cutscene.hideHud()) {
			MinecraftClient.getInstance().options.hudHidden = true;
		}

		InstantLore.LOGGER.info("Cutscene started: {} s, frames {}, anchor ({}, {}, {}), turn {}°",
				cutscene.duration(), cutscene.keyframes().size(),
				String.format("%.1f", origin.x), String.format("%.1f", origin.y),
				String.format("%.1f", origin.z), Math.round(originYaw));
	}

	public static void stop() {
		if (active == null) {
			return;
		}

		active = null;

		MinecraftClient.getInstance().options.hudHidden = savedHudHidden;

		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.setYaw(savedYaw);
			player.setPitch(savedPitch);
		}

		InstantLore.LOGGER.info("Cutscene finished");
	}

	private static double elapsedSeconds() {
		return (System.nanoTime() - startedAtNanos) / 1_000_000_000.0;
	}

	public static Vec3d cameraPos() {
		Cutscene cutscene = active;

		if (cutscene == null) {
			return null;
		}

		double t = elapsedSeconds();

		if (t >= cutscene.duration()) {
			stop();
			return null;
		}

		Vec3d position = interpolate(cutscene, t, true);
		sample(cutscene, t, position);
		return position;
	}

	private static void sample(Cutscene cutscene, double t, Vec3d position) {
		if (!cutscene.debug()) {
			return;
		}

		int second = (int) t;

		if (second != lastLoggedSecond) {
			lastLoggedSecond = second;
			float[] rotation = cameraRotation();
			InstantLore.LOGGER.info("Cutscene t={}s: camera ({}, {}, {}) rotation ({}, {}) roll {}",
					second,
					String.format("%.1f", position.x),
					String.format("%.1f", position.y),
					String.format("%.1f", position.z),
					rotation == null ? "?" : String.format("%.0f", rotation[0]),
					rotation == null ? "?" : String.format("%.0f", rotation[1]),
					String.format("%.0f", cameraRoll()));
		}
	}

	public static boolean hidesHud() {
		Cutscene cutscene = active;
		return cutscene == null || cutscene.hideHud();
	}

	public static float[] cameraRotation() {
		Cutscene cutscene = active;

		if (cutscene == null) {
			return null;
		}

		double t = elapsedSeconds();
		Vec3d from = interpolate(cutscene, t, true);
		Vec3d to = interpolate(cutscene, t, false);
		Vec3d direction = to.subtract(from);

		if (direction.lengthSquared() < 1.0e-6) {
			return new float[]{savedYaw, savedPitch};
		}

		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

		float yaw = MathHelper.wrapDegrees(
				(float) (MathHelper.atan2(direction.z, direction.x) * 180.0 / Math.PI) - 90.0F);
		float pitch = MathHelper.wrapDegrees(
				(float) -(MathHelper.atan2(direction.y, horizontal) * 180.0 / Math.PI));

		return new float[]{yaw, pitch};
	}

	private static Vec3d interpolate(Cutscene cutscene, double t, boolean camera) {
		Cutscene.Keyframe[] pair = surrounding(cutscene, t);
		double eased = easedProgress(cutscene, pair[0], pair[1], t);

		double[] a = camera ? pair[0].camera() : pair[0].lookAt();
		double[] b = camera ? pair[1].camera() : pair[1].lookAt();

		return place(
				MathHelper.lerp(eased, a[0], b[0]),
				MathHelper.lerp(eased, a[1], b[1]),
				MathHelper.lerp(eased, a[2], b[2]));
	}

	public static float cameraRoll() {
		Cutscene cutscene = active;

		if (cutscene == null) {
			return 0.0F;
		}

		double t = elapsedSeconds();
		Cutscene.Keyframe[] pair = surrounding(cutscene, t);
		double eased = easedProgress(cutscene, pair[0], pair[1], t);

		return (float) MathHelper.lerp(eased, pair[0].roll(), pair[1].roll());
	}

	private static Cutscene.Keyframe[] surrounding(Cutscene cutscene, double t) {
		var frames = cutscene.keyframes();

		for (int i = 0; i < frames.size() - 1; i++) {
			if (t >= frames.get(i).time() && t <= frames.get(i + 1).time()) {
				return new Cutscene.Keyframe[]{frames.get(i), frames.get(i + 1)};
			}
		}

		return new Cutscene.Keyframe[]{frames.get(0), frames.get(frames.size() - 1)};
	}

	private static double easedProgress(Cutscene cutscene, Cutscene.Keyframe previous,
			Cutscene.Keyframe next, double t) {
		double span = next.time() - previous.time();
		double progress = span <= 0 ? 0 : MathHelper.clamp((t - previous.time()) / span, 0, 1);
		return ease(cutscene.easing(), progress);
	}

	private static Vec3d place(double dx, double dy, double dz) {
		if (originYaw == 0.0F) {
			return new Vec3d(origin.x + dx, origin.y + dy, origin.z + dz);
		}

		double radians = Math.toRadians(originYaw);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);

		return new Vec3d(
				origin.x + dx * cos - dz * sin,
				origin.y + dy,
				origin.z + dx * sin + dz * cos);
	}

	private static double ease(String easing, double x) {
		return "linear".equals(easing) ? x : x * x * (3 - 2 * x);
	}
}
