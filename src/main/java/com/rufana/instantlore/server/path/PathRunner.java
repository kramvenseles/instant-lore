package com.rufana.instantlore.server.path;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.LoreEventPayload;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.LoreReportBus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PathRunner {
	private static final int MAX_FRUITLESS = 3;

	private static final int MAX_SEGMENTS = 64;

	private static final double GOAL_REACHED = 1.5;

	private static final long MIN_REPLAN_NANOS = 500_000_000L;

	private static final int MAX_RAPID_PLANS = 5;

	private static final Map<UUID, Walk> WALKS = new ConcurrentHashMap<>();

	private PathRunner() {
	}

	private static final class Walk {
		final BlockPos goal;
		final String speed;

		final java.util.function.BiConsumer<Boolean, String> onFinish;
		int segments;
		int fruitless;
		int rapidPlans;
		long lastPlanAt;
		double bestDistance = Double.MAX_VALUE;

		boolean lastSegmentComplete;

		Walk(BlockPos goal, String speed,
				java.util.function.BiConsumer<Boolean, String> onFinish) {
			this.goal = goal;
			this.speed = speed;
			this.onFinish = onFinish;
		}
	}

	public static void register() {
		LoreReportBus.subscribe((player, type, data) -> {
			Walk walk = WALKS.get(player.getUuid());

			if (walk == null) {
				return;
			}

			switch (type) {
				case "player.arrived" -> advance(player, walk, true);
				case "player.stuck" -> {
					LoreDevLog.info("{} got stuck on the road - replanning",
							player.getName().getString());
					advance(player, walk, false);
				}
				case "player.control_ended" -> {
					if (WALKS.containsKey(player.getUuid())) {
						advance(player, walk, false);
					}
				}
				default -> {
				}
			}
		});

		//
		//
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			Walk walk = WALKS.get(player.getUuid());

			if (walk != null) {
				LoreDevLog.info("{} is back on the road - continuing guidance",
						player.getName().getString());
				plan(player, walk);
			}
		});
	}

	public static void walkTo(ServerPlayerEntity player, BlockPos goal, String speed) {
		walkTo(player, goal, speed, null);
	}

	public static void walkTo(ServerPlayerEntity player, BlockPos goal, String speed,
			java.util.function.BiConsumer<Boolean, String> onFinish) {
		WALKS.put(player.getUuid(), new Walk(goal, speed, onFinish));
		plan(player, WALKS.get(player.getUuid()));
	}

	public static void cancel(ServerPlayerEntity player) {
		if (WALKS.remove(player.getUuid()) != null) {
			send(player, "player.release", "{}");
			player.sendMessage(Text.literal("Дорога отменена"), false);
		}
	}

	private static void advance(ServerPlayerEntity player, Walk walk, boolean arrived) {
		if (arrived && walk.lastSegmentComplete) {
			finish(player, "дошёл до цели");
			return;
		}

		double distance = Math.sqrt(player.squaredDistanceTo(
				walk.goal.getX() + 0.5, walk.goal.getY(), walk.goal.getZ() + 0.5));

		if (distance < walk.bestDistance - 0.5) {
			walk.bestDistance = distance;
			walk.fruitless = 0;
		} else if (++walk.fruitless >= MAX_FRUITLESS) {
			finish(player, "не приближается к цели, осталось " + Math.round(distance) + " блока");
			return;
		}

		plan(player, walk);
	}

	private static void plan(ServerPlayerEntity player, Walk walk) {
		if (++walk.segments > MAX_SEGMENTS) {
			finish(player, "исчерпан лимит отрезков");
			return;
		}

		long started = System.nanoTime();

		if (started - walk.lastPlanAt < MIN_REPLAN_NANOS) {
			if (++walk.rapidPlans > MAX_RAPID_PLANS) {
				finish(player, "отрезки завершаются мгновенно — дорога не строится");
				return;
			}
		} else {
			walk.rapidPlans = 0;
		}

		walk.lastPlanAt = started;
		LorePathfinder.Result result = LorePathfinder.find(
				player.getEntityWorld(), player.getBlockPos(), walk.goal);

		long micros = (System.nanoTime() - started) / 1000;

		if (result.empty()) {
			finish(player, "маршрут не построен (" + result.note() + ")");
			return;
		}

		if (result.points().size() < 2) {
			//
			double left = Math.sqrt(player.squaredDistanceTo(
					walk.goal.getX() + 0.5, player.getY(), walk.goal.getZ() + 0.5));

			finish(player, left <= GOAL_REACHED
					? "дошёл до цели"
					: "дальше хода нет, до цели осталось " + Math.round(left) + " блока");
			return;
		}

		walk.lastSegmentComplete = result.complete();

		//
		LoreDevLog.info("Segment {} for {}: {} → {}, points {}, horizon {}, {} - {} µs",
				walk.segments, player.getName().getString(),
				pretty(player.getBlockPos()), pretty(walk.goal), result.points().size(),
				LorePathfinder.horizon(player.getEntityWorld()), result.note(), micros);
		LoreDevLog.info("  points: {}", points(result.points()));

		send(player, "player.control", "{\"timeout\":60}");
		send(player, "player.path", pathJson(player.getEntityWorld(), result.points(), walk.speed));

		if (result.complete()) {
			return;
		}

		InstantLore.LOGGER.debug("Segment incomplete, road will be completed");
	}

	private static void finish(ServerPlayerEntity player, String why) {
		Walk walk = WALKS.remove(player.getUuid());
		send(player, "player.release", "{}");

		LoreDevLog.info("Road for {} finished: {}",
				player.getName().getString(), why);

		if (walk == null || walk.onFinish == null) {
			player.sendMessage(Text.literal("Дорога закончена: " + why), false);
			return;
		}

		walk.onFinish.accept("дошёл до цели".equals(why), why);
	}

	private static String pretty(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static String points(List<BlockPos> points) {
		StringBuilder text = new StringBuilder();

		for (int i = 0; i < points.size(); i++) {
			if (i == 8 && points.size() > 10) {
				text.append(" … (+").append(points.size() - 9).append(") ");
				i = points.size() - 1;
			}

			text.append(i == 0 ? "" : " → ").append('[').append(pretty(points.get(i))).append(']');
		}

		return text.toString();
	}

	private static String pathJson(ServerWorld world, List<BlockPos> points, String speed) {
		StringBuilder json = new StringBuilder("{\"speed\":\"").append(speed)
				.append("\",\"points\":[");

		for (int i = 0; i < points.size(); i++) {
			Vec3d point = LorePathfinder.aim(world, points.get(i));

			boolean crouch = i > 0
					&& LorePathfinder.crouchAlong(world, points.get(i - 1), points.get(i));

			json.append(i == 0 ? "" : ",")
					.append('[').append(round(point.x)).append(',')
					.append(round(point.y)).append(',')
					.append(round(point.z))
					.append(crouch ? ",\"sneak\"" : "")
					.append(']');
		}

		return json.append("]}").toString();
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static void send(ServerPlayerEntity player, String type, String json) {
		ServerPlayNetworking.send(player, new LoreEventPayload(type, json));
	}
}
