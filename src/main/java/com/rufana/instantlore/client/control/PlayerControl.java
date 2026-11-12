package com.rufana.instantlore.client.control;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.LoreReports;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class PlayerControl {
	private static final double MAX_TIMEOUT_SECONDS = 60.0;
	private static final double DEFAULT_TIMEOUT_SECONDS = 30.0;

	private static final double DEFAULT_ARRIVE = 0.15;

	private static final double WAYPOINT_ARRIVE = 0.9;

	private static final double LANDED_SLACK = 1.6;

	private static final double SLOW_DISTANCE = 2.5;
	private static final double CREEP_DISTANCE = 0.8;

	private static final double DEFAULT_TURN_RATE = 360.0;

	private static final double WALK_CONE_DEGREES = 75.0;

	private static final double SPRINT_CONE_DEGREES = 20.0;

	private static final double STEER_LOOKAHEAD = 3.0;

	private static final double LOOKAHEAD_MAX_TURN_DEGREES = 45.0;

	private static final long JUMP_COOLDOWN_NANOS = 500_000_000L;

	private static final int COLLISION_TICKS_TO_JUMP = 5;

	private static final double STEP_UP_HEIGHT = 0.6;

	private static final double SINK_SLACK = 0.25;

	private static final double WATER_ARRIVE = 0.7;

	private static final double VERTICAL_ARRIVE = 1.5;

	private static final double OVERHEAD_RADIUS = 0.35;

	private static final double OVERHEAD_HEIGHT = 0.5;

	private static final double CENTERED_ARRIVE = 0.2;

	private static final double CLOSE_RANGE = 0.8;

	private static final double ARRIVE_SLACK = 0.5;

	private static final double STUCK_DISTANCE = 0.15;
	private static final long STUCK_NANOS = 2_000_000_000L;

	private static volatile Session session;

	private PlayerControl() {
	}

	public static final class Session {
		final boolean lockLook;
		final boolean lockMove;
		final boolean ignoreExternalVelocity;
		final boolean noEntityPush;
		final long startedAt;
		final long expiresAt;

		final java.util.ArrayDeque<Waypoint> path = new java.util.ArrayDeque<>();
		volatile Vec3d target;

		volatile String targetSpeed;
		volatile String speed = "walk";
		volatile double arrive = DEFAULT_ARRIVE;

		volatile boolean facePath = true;
		volatile double turnRate = DEFAULT_TURN_RATE;

		volatile Vec3d lookAt;
		volatile Float lookYaw;
		volatile Float lookPitch;

		volatile boolean lookReported;

		volatile RawInput raw;

		Vec3d lastPosition;
		long lastProgressAt;
		long lastJumpAt;

		int collisionTicks;

		Session(boolean lockLook, boolean lockMove, boolean ignoreExternalVelocity,
				boolean noEntityPush, double timeoutSeconds) {
			this.lockLook = lockLook;
			this.lockMove = lockMove;
			this.ignoreExternalVelocity = ignoreExternalVelocity;
			this.noEntityPush = noEntityPush;
			this.startedAt = System.nanoTime();
			this.expiresAt = startedAt + (long) (timeoutSeconds * 1_000_000_000L);
		}
	}

	public static void begin(boolean lockLook, boolean lockMove,
			boolean ignoreExternalVelocity, boolean noEntityPush, double timeoutSeconds) {
		double timeout = Double.isFinite(timeoutSeconds) && timeoutSeconds > 0
				? Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS)
				: DEFAULT_TIMEOUT_SECONDS;

		session = new Session(lockLook, lockMove, ignoreExternalVelocity, noEntityPush, timeout);

		ClickControl.reset();

		InstantLore.LOGGER.info("Control taken over for {} s (look {}, feet {})",
				timeout, lockLook ? "locked" : "free",
				lockMove ? "locked" : "free");
	}

	public static void release(String why) {
		if (session == null) {
			return;
		}

		session = null;
		ClickControl.reset();
		forgetSmoothing();
		InstantLore.LOGGER.info("Control returned to the player: {}", why);

		JsonObject data = new JsonObject();
		data.addProperty("reason", why);
		LoreReports.send("player.control_ended", data);
	}

	public record Waypoint(Vec3d at, String speed) {
		public Waypoint(Vec3d at) {
			this(at, null);
		}
	}

	public static void setTarget(Vec3d target, String speed, double arrive, boolean facePath) {
		setPath(target == null
						? java.util.List.<Waypoint>of()
						: java.util.List.of(new Waypoint(target)),
				speed, arrive, facePath);
	}

	public record RawInput(
			float forward, float strafe,
			boolean jump, boolean sneak, boolean sprint,
			long untilNanos
	) {
		boolean expired() {
			return untilNanos > 0 && System.nanoTime() >= untilNanos;
		}

		PlayerInput keys() {
			return new PlayerInput(
					forward > AXIS_EPSILON, forward < -AXIS_EPSILON,
					strafe > AXIS_EPSILON, strafe < -AXIS_EPSILON,
					jump, sneak, sprint);
		}
	}

	private static final float AXIS_EPSILON = 0.001F;

	private static final double MAX_INPUT_SECONDS = 30.0;

	public static void input(double forward, double strafe,
			boolean jump, boolean sneak, boolean sprint, double seconds) {
		Session current = active();

		if (current == null) {
			InstantLore.LOGGER.warn("Key presses received but there is no control session - ignoring");
			return;
		}

		double held = Double.isFinite(seconds) && seconds > 0
				? Math.min(seconds, MAX_INPUT_SECONDS)
				: 0.0;

		current.path.clear();
		current.target = null;
		current.targetSpeed = null;
		current.raw = new RawInput(
				axis(forward), axis(strafe), jump, sneak, sprint,
				held > 0 ? System.nanoTime() + (long) (held * 1_000_000_000L) : 0L);
	}

	public static void releaseInput() {
		Session current = active();

		if (current != null) {
			current.raw = null;
		}
	}

	private static float axis(double value) {
		return Double.isFinite(value) ? (float) Math.clamp(value, -1.0, 1.0) : 0.0F;
	}

	private static RawInput rawInput() {
		Session current = active();

		if (current == null) {
			return null;
		}

		RawInput raw = current.raw;

		if (raw == null) {
			return null;
		}

		if (raw.expired()) {
			current.raw = null;
			return null;
		}

		return raw;
	}

	public static boolean hasRawInput() {
		return rawInput() != null;
	}

	public static Vec2f rawMovement() {
		RawInput raw = rawInput();
		return raw == null ? null : new Vec2f(raw.strafe(), raw.forward());
	}

	private static void takeNext(Session current) {
		Waypoint next = current.path.poll();

		current.target = next == null ? null : next.at();
		current.targetSpeed = next == null ? null : next.speed();
	}

	public static void setPath(java.util.List<Waypoint> points, String speed, double arrive,
			boolean facePath) {
		Session current = active();

		if (current == null) {
			InstantLore.LOGGER.warn("Route received but there is no control session - ignoring");
			return;
		}

		current.raw = null;
		current.path.clear();
		current.path.addAll(points);
		takeNext(current);
		current.speed = speed == null ? "walk" : speed;
		current.arrive = Double.isFinite(arrive) && arrive > 0 ? arrive : DEFAULT_ARRIVE;
		current.facePath = facePath;
		current.lastPosition = null;
		current.lastProgressAt = System.nanoTime();
	}

	public static void lookAt(Vec3d point, double turnRate) {
		Session current = active();

		if (current == null) {
			InstantLore.LOGGER.warn("Look received but there is no control session - ignoring");
			return;
		}

		current.lookAt = point;
		current.lookYaw = null;
		current.lookPitch = null;
		current.lookReported = false;
		current.turnRate = sanitizeTurnRate(turnRate);
	}

	public static void lookAngles(Float yaw, Float pitch, double turnRate) {
		Session current = active();

		if (current == null) {
			InstantLore.LOGGER.warn("Look received but there is no control session - ignoring");
			return;
		}

		current.lookAt = null;
		current.lookYaw = yaw;
		current.lookPitch = pitch;
		current.lookReported = false;
		current.turnRate = sanitizeTurnRate(turnRate);
	}

	public static void freeLook() {
		Session current = active();

		if (current != null) {
			current.lookAt = null;
			current.lookYaw = null;
			current.lookPitch = null;
			current.lookReported = false;
		}
	}

	private static double sanitizeTurnRate(double requested) {
		if (!Double.isFinite(requested) || requested <= 0) {
			return DEFAULT_TURN_RATE;
		}

		return requested;
	}

	public static Session active() {
		Session current = session;

		if (current == null) {
			return null;
		}

		if (System.nanoTime() >= current.expiresAt) {
			release("вышло время");
			return null;
		}

		return current;
	}

	public static boolean isActive() {
		return active() != null;
	}

	public static boolean isLookLocked() {
		Session current = active();
		return current != null && current.lockLook;
	}

	public static boolean isMoveLocked() {
		Session current = active();
		return current != null && current.lockMove;
	}

	public static boolean ignoresExternalVelocity() {
		Session current = active();
		return current != null && current.ignoreExternalVelocity;
	}

	public static boolean blocksEntityPush() {
		Session current = active();
		return current != null && current.noEntityPush;
	}

	public static PlayerInput computeInput() {
		Session current = active();
		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (current == null || player == null) {
			return PlayerInput.DEFAULT;
		}

		RawInput raw = rawInput();

		if (raw != null) {
			return raw.keys();
		}

		if (current.target == null) {
			return PlayerInput.DEFAULT;
		}

		Vec3d position = new Vec3d(player.getX(), player.getY(), player.getZ());
		Vec3d toTarget = current.target.subtract(position);
		double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		boolean inWater = player.isTouchingWater();

		double tolerance = current.path.isEmpty()
				? current.arrive
				: Math.max(current.arrive, WAYPOINT_ARRIVE);

		if (inWater) {
			tolerance = Math.max(tolerance, WATER_ARRIVE);
		}

		//
		//
		if (climbsNext(current)) {
			tolerance = Math.min(tolerance, CENTERED_ARRIVE);
		}

		//
		//
		//
		boolean descending = toTarget.y < -STEP_UP_HEIGHT;
		boolean verticallyThere = descending
				? Math.abs(toTarget.y) <= STEP_UP_HEIGHT && (player.isOnGround() || inWater)
				: Math.abs(toTarget.y) <= VERTICAL_ARRIVE;

		//
		double horizontalNeeded = current.path.isEmpty()
				? tolerance
				: Math.max(tolerance, LANDED_SLACK);

		//
		Vec3d nextPoint = peekAt(current);
		boolean passed = descending && nextPoint != null
				&& horizontalTo(player, nextPoint) <= horizontal;

		boolean atPoint = verticallyThere
				&& (passed || horizontal <= (descending ? horizontalNeeded : tolerance));

		if (atPoint) {
			takeNext(current);
			current.lastPosition = null;

			if (current.target == null) {
				InstantLore.LOGGER.info("Route completed, {} blocks remaining (tolerance {})",
						String.format("%.3f", horizontal), current.arrive);

				JsonObject data = new JsonObject();
				data.addProperty("remaining", Math.round(horizontal * 1000.0) / 1000.0);
				LoreReports.sendAt("player.arrived", position.x, position.y, position.z, data);

				return PlayerInput.DEFAULT;
			}

			InstantLore.LOGGER.debug("Point reached, {} remaining", current.path.size() + 1);

			JsonObject data = new JsonObject();
			data.addProperty("left", current.path.size() + 1);
			LoreReports.sendAt("player.waypoint", position.x, position.y, position.z, data);

			return computeInput();
		}

		//
		if (current.path.isEmpty() && horizontal <= current.arrive + ARRIVE_SLACK
				&& isStuck(current, position, inWater)) {
			current.target = null;

			InstantLore.LOGGER.info("Cannot get closer, {} blocks remaining - considering it reached",
					String.format("%.3f", horizontal));

			JsonObject data = new JsonObject();
			data.addProperty("remaining", Math.round(horizontal * 1000.0) / 1000.0);
			data.addProperty("blocked", true);
			LoreReports.sendAt("player.arrived", position.x, position.y, position.z, data);

			return PlayerInput.DEFAULT;
		}

		if (isStuck(current, position, inWater)) {
			current.path.clear();
			current.target = null;
			InstantLore.LOGGER.warn("Got stuck short of the target - route cleared");

			JsonObject data = new JsonObject();
			data.addProperty("remaining", Math.round(horizontal * 1000.0) / 1000.0);
			LoreReports.sendAt("player.stuck", position.x, position.y, position.z, data);

			return PlayerInput.DEFAULT;
		}

		String requested = current.targetSpeed == null ? current.speed : current.targetSpeed;

		String speed = current.path.isEmpty()
				? speedFor(requested, horizontal)
				: requested;

		if (inWater && "sneak".equals(speed)) {
			speed = "walk";
		}

		boolean sneak = "sneak".equals(speed);

		//
		//
		boolean sink = inWater && current.target != null
				&& player.getY() > current.target.y + SINK_SLACK;

		boolean descends = current.target != null
				&& current.target.y < player.getY() - STEP_UP_HEIGHT;
		boolean sprint = "sprint".equals(speed) && !descends;

		long now = System.nanoTime();
		current.collisionTicks = player.horizontalCollision ? current.collisionTicks + 1 : 0;
		boolean jump = wantsJump(current, player, inWater, now);

		if (jump) {
			current.lastJumpAt = now;
		}

		if (horizontal < OVERHEAD_RADIUS && toTarget.y > OVERHEAD_HEIGHT) {
			if (now - lastReportAt > 1_000_000_000L) {
				lastReportAt = now;
				InstantLore.LOGGER.info("Steering: point overhead, {} away in height{}",
						String.format("%.2f", toTarget.y), inWater ? ", surfacing" : "");
			}

			return new PlayerInput(false, false, false, false, jump, false, false);
		}

		if (!current.facePath || horizontal < CLOSE_RANGE) {
			//
			boolean precise = current.path.isEmpty() && !inWater;
			double yawRadians = Math.toRadians(player.getYaw());
			double forwardX = -MathHelper.sin((float) yawRadians);
			double forwardZ = MathHelper.cos((float) yawRadians);

			double forward = (toTarget.x * forwardX + toTarget.z * forwardZ) / horizontal;
			double strafe = (toTarget.x * forwardZ - toTarget.z * forwardX) / horizontal;

			return new PlayerInput(
					forward > 0.3, forward < -0.3, strafe > 0.3, strafe < -0.3,
					jump, sneak || precise || sink, sprint && !precise && forward > 0.7);
		}

		//
		float desiredYaw = yawTo(steering(current, position, toTarget, horizontal));
		double error = Math.abs(MathHelper.wrapDegrees(desiredYaw - player.getYaw()));

		boolean walk = error < WALK_CONE_DEGREES;

		if (now - lastReportAt > 1_000_000_000L) {
			lastReportAt = now;
			InstantLore.LOGGER.info(
					"Steering: {} blocks to target, heading error {}°, mode {}{}{}",
					String.format("%.2f", horizontal), String.format("%.0f", error), speed,
					walk ? "" : " (доворачиваюсь)", jump ? " (прыжок)" : "");
		}

		return new PlayerInput(walk, false, false, false,
				jump && (walk || inWater), sneak || sink,
				sprint && walk && error < SPRINT_CONE_DEGREES);
	}

	private static Vec3d steering(Session current, Vec3d position, Vec3d toTarget,
			double horizontal) {
		if (horizontal > STEER_LOOKAHEAD) {
			return toTarget;
		}

		Vec3d next = peekAt(current);

		if (next == null) {
			return toTarget;
		}

		Vec3d ahead = next.subtract(position);

		return turn(toTarget, ahead) > LOOKAHEAD_MAX_TURN_DEGREES ? toTarget : ahead;
	}

	private static double turn(Vec3d from, Vec3d to) {
		double ax = from.x, az = from.z, bx = to.x, bz = to.z;
		double lengths = Math.sqrt(ax * ax + az * az) * Math.sqrt(bx * bx + bz * bz);

		if (lengths < 1.0e-6) {
			return 0.0;
		}

		return Math.toDegrees(Math.acos(
				Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / lengths))));
	}

	private static boolean wantsJump(Session current, ClientPlayerEntity player,
			boolean inWater, long now) {
		Vec3d target = current.target;

		if (inWater) {
			return target == null || target.y >= player.getY() - 0.5;
		}

		if (!player.isOnGround() || now - current.lastJumpAt <= JUMP_COOLDOWN_NANOS) {
			return false;
		}

		//
		if (target != null && target.y < player.getY() - STEP_UP_HEIGHT) {
			return false;
		}

		boolean stepUp = target != null && target.y - player.getY() > STEP_UP_HEIGHT
				&& horizontalTo(player, target) < 2.0;

		return stepUp || current.collisionTicks >= COLLISION_TICKS_TO_JUMP;
	}

	private static Vec3d peekAt(Session current) {
		Waypoint next = current.path.peek();
		return next == null ? null : next.at();
	}

	private static boolean climbsNext(Session current) {
		Vec3d target = current.target;
		Vec3d next = peekAt(current);

		if (target == null || next == null || next.y <= target.y + 0.5) {
			return false;
		}

		return Math.abs(next.x - target.x) < 0.5 && Math.abs(next.z - target.z) < 0.5;
	}

	private static double horizontalTo(ClientPlayerEntity player, Vec3d point) {
		double dx = point.x - player.getX();
		double dz = point.z - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static long lastReportAt;

	public static float[] computeLook() {
		Session current = active();
		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (current == null || player == null) {
			forgetSmoothing();
			return null;
		}

		Float wantYaw = current.lookYaw;
		Float wantPitch = current.lookPitch;

		if (current.lookAt != null) {
			Vec3d eye = player.getEyePos();
			Vec3d delta = current.lookAt.subtract(eye);
			double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

			wantYaw = yawTo(delta);
			wantPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
		} else if (wantYaw == null && wantPitch == null && current.facePath
				&& current.target != null && current.lockLook) {
			//
			wantYaw = yawTo(current.target.subtract(
					new Vec3d(player.getX(), player.getY(), player.getZ())));
		}

		if (wantYaw == null && wantPitch == null) {
			forgetSmoothing();
			return null;
		}

		float step = (float) (current.turnRate / 20.0);

		float yaw = wantYaw == null ? player.getYaw() : approach(player.getYaw(), wantYaw, step);
		float pitch = wantPitch == null
				? player.getPitch()
				: MathHelper.clamp(approach(player.getPitch(), wantPitch, step), -90.0F, 90.0F);

		boolean explicit = current.lookAt != null || current.lookYaw != null
				|| current.lookPitch != null;

		rememberForSmoothing(player.getYaw(), player.getPitch(), yaw, pitch);

		if (explicit && !current.lookReported && aimed(wantYaw, yaw) && aimed(wantPitch, pitch)) {
			current.lookReported = true;

			JsonObject data = new JsonObject();
			data.addProperty("yaw", Math.round(yaw * 10.0) / 10.0);
			data.addProperty("pitch", Math.round(pitch * 10.0) / 10.0);
			LoreReports.send("player.looked", data);
		}

		return new float[] {yaw, pitch};
	}


	private static volatile boolean smoothing;
	private static volatile float smoothFromYaw;
	private static volatile float smoothFromPitch;
	private static volatile float smoothToYaw;
	private static volatile float smoothToPitch;

	private static void rememberForSmoothing(float fromYaw, float fromPitch,
			float toYaw, float toPitch) {
		smoothFromYaw = fromYaw;
		smoothFromPitch = fromPitch;
		smoothToYaw = toYaw;
		smoothToPitch = toPitch;
		smoothing = true;
	}

	private static void forgetSmoothing() {
		smoothing = false;
	}

	public static boolean isSmoothingLook() {
		return smoothing && isActive();
	}

	public static float smoothedYaw(float tickDelta) {
		return MathHelper.lerpAngleDegrees(MathHelper.clamp(tickDelta, 0.0F, 1.0F),
				smoothFromYaw, smoothToYaw);
	}

	public static float smoothedPitch(float tickDelta) {
		return MathHelper.lerp(MathHelper.clamp(tickDelta, 0.0F, 1.0F),
				smoothFromPitch, smoothToPitch);
	}

	private static boolean aimed(Float wanted, float actual) {
		return wanted == null || Math.abs(MathHelper.wrapDegrees(wanted - actual)) < 0.01;
	}

	private static float yawTo(Vec3d delta) {
		return MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
	}

	private static float approach(float from, float to, float step) {
		float difference = MathHelper.wrapDegrees(to - from);

		if (Math.abs(difference) <= step) {
			return to;
		}

		return from + Math.copySign(step, difference);
	}

	private static String speedFor(String requested, double remaining) {
		if (remaining <= CREEP_DISTANCE) {
			return "sneak";
		}

		if (remaining <= SLOW_DISTANCE) {
			return "walk";
		}

		return requested;
	}

	private static boolean isStuck(Session current, Vec3d position, boolean inWater) {
		long now = System.nanoTime();

		if (current.lastPosition == null) {
			current.lastPosition = position;
			current.lastProgressAt = now;
			return false;
		}

		//
		//
		//
		Vec3d moved = current.lastPosition.subtract(position);
		double progress = moved.horizontalLength();
		Vec3d target = current.target;

		if (inWater && target != null && target.y > position.y + 0.5) {
			progress += Math.max(0.0, position.y - current.lastPosition.y);
		}

		if (progress > STUCK_DISTANCE) {
			current.lastPosition = position;
			current.lastProgressAt = now;
			return false;
		}

		return now - current.lastProgressAt > STUCK_NANOS;
	}
}
