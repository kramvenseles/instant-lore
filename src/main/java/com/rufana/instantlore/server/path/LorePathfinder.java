package com.rufana.instantlore.server.path;

import com.rufana.instantlore.InstantLore;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class LorePathfinder {
	private static final int MAX_NODES = 20_000;

	private static final long MAX_NANOS = 15_000_000L;

	private static final int MAX_FALL = 3;

	private static final double JUMP_COST = 2.0;

	private static final double WATER_COST = 2.0;

	private static final double SUBMERGED_COST = 3.0;

	private static final double CROUCH_COST = 2.0;

	private static final double HUG_COST = 0.2;

	private static final int MAX_RADIUS = 128;

	private static final int HORIZON_MARGIN = 24;

	public static int horizon(ServerWorld world) {
		int loaded = world.getServer().getPlayerManager().getViewDistance() * 16;
		return Math.max(32, Math.min(MAX_RADIUS, loaded - HORIZON_MARGIN));
	}

	private LorePathfinder() {
	}

	public record Result(List<BlockPos> points, boolean complete, String note) {
		public boolean empty() {
			return points.isEmpty();
		}
	}

	private record Node(BlockPos pos, double cost, double estimate) {
	}

	public static Result find(ServerWorld world, BlockPos from, BlockPos to) {
		return find(world, from, to, horizon(world));
	}

	public static void warmUp(ServerWorld world) {
		BlockPos spawn = world.getSpawnPoint().getPos();
		long started = System.nanoTime();

		try {
			find(world, spawn, spawn.add(24, 0, 24), 32);
		} catch (Throwable failed) {
			InstantLore.LOGGER.warn("Pathfinding warm-up failed - no big deal", failed);
			return;
		}

		InstantLore.LOGGER.info("Pathfinding warmed up in {} µs",
				(System.nanoTime() - started) / 1000);
	}

	public static Result find(ServerWorld world, BlockPos from, BlockPos to, int radius) {
		BlockPos start = groundAt(world, from);
		String escape = "";

		if (start == null) {
			start = nearestStanding(world, from);

			if (start == null) {
				return new Result(List.of(), false,
						"стою в непроходимой клетке, и выйти из неё некуда");
			}

			escape = " (выход из непроходимой клетки: " + start.getX() + " " + start.getY()
					+ " " + start.getZ() + ")";
		}

		//
		//
		BlockPos resolved = groundAt(world, to);
		boolean goalKnown = resolved != null;

		if (!goalKnown) {
			resolved = nearestStanding(world, to);
			goalKnown = resolved != null;
		}

		BlockPos goal = goalKnown ? resolved : to;

		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		Map<BlockPos, Double> best = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>(
				(a, b) -> Double.compare(a.estimate(), b.estimate()));

		best.put(start, 0.0);
		open.add(new Node(start, 0.0, heuristic(start, goal)));

		long deadline = System.nanoTime() + MAX_NANOS;
		int expanded = 0;

		BlockPos closest = start;
		double closestDistance = heuristic(start, goal);

		while (!open.isEmpty()) {
			if (++expanded > MAX_NODES || System.nanoTime() > deadline) {
				return partial(world, cameFrom, closest, (goalKnown
						? (expanded > MAX_NODES ? "исчерпан бюджет узлов" : "исчерпано время")
								+ " на " + expanded + " узлах"
						: "иду в сторону цели, она ещё не в загруженных чанках") + escape);
			}

			Node current = open.poll();

			if (current.pos().equals(goal)) {
				return new Result(route(world, cameFrom, goal), true,
						"маршрут построен на " + expanded + " узлах" + escape);
			}

			double distance = heuristic(current.pos(), goal);

			if (distance < closestDistance) {
				closestDistance = distance;
				closest = current.pos();
			}

			for (Step step : steps(world, current.pos())) {
				if (!withinRadius(start, step.pos(), radius)) {
					continue;
				}

				double cost = best.get(current.pos()) + step.cost();
				Double known = best.get(step.pos());

				if (known != null && known <= cost) {
					continue;
				}

				best.put(step.pos(), cost);
				cameFrom.put(step.pos(), current.pos());
				open.add(new Node(step.pos(), cost, cost + heuristic(step.pos(), goal)));
			}
		}

		//
		return partial(world, cameFrom, closest, escape + (goalKnown
				? "прохода к цели нет"
				: "не нашёл, где цель: в " + to.getX() + " " + to.getY() + " " + to.getZ()
						+ " и вокруг негде ни встать, ни плыть"));
	}

	private static Result partial(ServerWorld world, Map<BlockPos, BlockPos> cameFrom,
			BlockPos closest, String note) {
		List<BlockPos> path = route(world, cameFrom, closest);

		if (path.size() < 2) {
			return new Result(List.of(), false, note);
		}

		return new Result(path, false, note);
	}

	private record Step(BlockPos pos, double cost) {
	}

	private static boolean withinRadius(BlockPos start, BlockPos pos, int radius) {
		return Math.abs(pos.getX() - start.getX()) <= radius
				&& Math.abs(pos.getZ() - start.getZ()) <= radius;
	}

	private static List<Step> steps(ServerWorld world, BlockPos from) {
		List<Step> steps = new ArrayList<>(10);
		boolean swimming = canSwim(world, from);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}

				boolean diagonal = dx != 0 && dz != 0;

				if (diagonal && !(passableSide(world, from, dx, 0) && passableSide(world, from, 0, dz))) {
					continue;
				}

				double base = diagonal ? 1.414 : 1.0;

				BlockPos up = from.add(dx, 1, dz);

				if (occupiable(world, up) && free(world, from.up(2))) {
					if (swimming || canSwim(world, up)) {
						offer(steps, world, from, up, base + travel(world, up));
						continue;
					}

					if (canStand(world, up)) {
						offer(steps, world, from, up, base + JUMP_COST);
						continue;
					}
				}

				BlockPos level = from.add(dx, 0, dz);

				if (occupiable(world, level)) {
					offer(steps, world, from, level, base + travel(world, level) + hug(world, from, dx, dz));
					continue;
				}

				//
				//
				if (!free(world, level, STEP_OVER) || !free(world, level.up())) {
					continue;
				}

				for (int drop = 1; drop <= MAX_FALL; drop++) {
					BlockPos down = from.add(dx, -drop, dz);

					if (occupiable(world, down)) {
						if (drop > 1 && !lands(world, down, dx, dz)) {
							break;
						}

						offer(steps, world, from, down, base + travel(world, down) + drop * 0.3);
						break;
					}

					if (blocked(world, down)) {
						break;
					}
				}
			}
		}

		if (swimming) {
			BlockPos up = from.up();

			if (occupiable(world, up)) {
				offer(steps, world, from, up, travel(world, up));
			}

			BlockPos down = from.down();

			if (canSwim(world, down)) {
				offer(steps, world, from, down, travel(world, down));
			}
		}

		return steps;
	}

	private static void offer(List<Step> steps, ServerWorld world, BlockPos from, BlockPos to,
			double cost) {
		if (sealed(world, from, to)) {
			return;
		}

		steps.add(new Step(to, cost));
	}

	private static boolean sealed(ServerWorld world, BlockPos from, BlockPos to) {
		//
		//
		double height = crouchOnly(world, from) || crouchOnly(world, to)
				? CROUCH_HEIGHT
				: PLAYER_HEIGHT;

		Box corridor = corridor(from, to, height);

		return hits(world, from, corridor) || hits(world, from.up(), corridor)
				|| hits(world, to, corridor) || hits(world, to.up(), corridor);
	}

	private static Box corridor(BlockPos from, BlockPos to, double height) {
		double x1 = Math.min(from.getX(), to.getX()) + 0.5 - PLAYER_HALF_WIDTH;
		double x2 = Math.max(from.getX(), to.getX()) + 0.5 + PLAYER_HALF_WIDTH;
		double z1 = Math.min(from.getZ(), to.getZ()) + 0.5 - PLAYER_HALF_WIDTH;
		double z2 = Math.max(from.getZ(), to.getZ()) + 0.5 + PLAYER_HALF_WIDTH;
		double y1 = Math.min(from.getY(), to.getY()) + STEP_OVER;
		double y2 = Math.max(from.getY(), to.getY()) + height - TOUCH;

		return new Box(x1, y1, z1, x2, y2, z2);
	}

	private static final double PLAYER_HEIGHT = 1.8;

	private static final double CROUCH_HEIGHT = 1.5;

	private static final double TOUCH = 1.0e-4;

	private static boolean hits(ServerWorld world, BlockPos pos, Box corridor) {
		BlockState state = stateOf(world, pos);

		if (state == null) {
			return true;
		}

		VoxelShape shape = state.getCollisionShape(world, pos);

		if (shape.isEmpty()) {
			return false;
		}

		return VoxelShapes.matchesAnywhere(
				shape.offset(pos.getX(), pos.getY(), pos.getZ()),
				VoxelShapes.cuboid(corridor), BooleanBiFunction.AND);
	}

	private static double travel(ServerWorld world, BlockPos pos) {
		if (!water(world, pos)) {
			return canCrouch(world, pos) ? CROUCH_COST : 0.0;
		}

		return water(world, pos.up()) ? SUBMERGED_COST : WATER_COST;
	}

	private static double hug(ServerWorld world, BlockPos from, int dx, int dz) {
		BlockPos target = from.add(dx, 0, dz);
		double penalty = 0.0;

		if (blocked(world, target.add(dz, 0, dx))) {
			penalty += HUG_COST;
		}

		if (blocked(world, target.add(-dz, 0, -dx))) {
			penalty += HUG_COST;
		}

		return penalty;
	}

	private static boolean passableSide(ServerWorld world, BlockPos from, int dx, int dz) {
		return occupiable(world, from.add(dx, 0, dz)) || occupiable(world, from.add(dx, 1, dz));
	}

	public static boolean canStandAt(ServerWorld world, BlockPos pos) {
		return canStand(world, pos);
	}

	private static boolean lands(ServerWorld world, BlockPos landing, int dx, int dz) {
		BlockPos beyond = landing.add(dx, 0, dz);

		return occupiable(world, beyond) || blocked(world, beyond);
	}

	private static boolean occupiable(ServerWorld world, BlockPos pos) {
		return canStand(world, pos) || canSwim(world, pos) || canCrouch(world, pos);
	}

	private static boolean canStand(ServerWorld world, BlockPos pos) {
		return free(world, pos, STEP_OVER) && free(world, pos.up()) && solid(world, pos.down());
	}

	private static final double CROUCH_HEAD = CROUCH_HEIGHT - 1.0;

	private static boolean canCrouch(ServerWorld world, BlockPos pos) {
		//
		//
		if (!free(world, pos, STEP_OVER) || !solid(world, pos.down())) {
			return false;
		}

		if (free(world, pos.up())) {
			return false;
		}

		return free(world, pos.up(), 0.0, CROUCH_HEAD);
	}

	public static boolean crouchOnly(ServerWorld world, BlockPos pos) {
		return canCrouch(world, pos);
	}

	private static boolean canSwim(ServerWorld world, BlockPos pos) {
		return water(world, pos) && free(world, pos.up());
	}

	private static boolean water(ServerWorld world, BlockPos pos) {
		BlockState state = stateOf(world, pos);
		return state != null && state.getFluidState().isIn(FluidTags.WATER)
				&& !state.blocksMovement();
	}

	private static boolean free(ServerWorld world, BlockPos pos) {
		return free(world, pos, 0.0);
	}

	private static boolean free(ServerWorld world, BlockPos pos, double floor) {
		return free(world, pos, floor, 1.0);
	}

	private static boolean free(ServerWorld world, BlockPos pos, double floor, double ceiling) {
		BlockState state = stateOf(world, pos);

		if (state == null) {
			return false;
		}

		if (state.getFluidState().isIn(FluidTags.LAVA)) {
			return false;
		}

		if (!state.blocksMovement()) {
			return true;
		}

		if (state.isFullCube(world, pos)) {
			return false;
		}

		VoxelShape shape = state.getCollisionShape(world, pos);

		if (shape.isEmpty()) {
			return true;
		}

		return !VoxelShapes.matchesAnywhere(shape.offset(pos.getX(), pos.getY(), pos.getZ()),
				VoxelShapes.cuboid(body(pos)
						.withMinY(pos.getY() + floor)
						.withMaxY(pos.getY() + ceiling - TOUCH)),
				BooleanBiFunction.AND);
	}

	private static final double STEP_OVER = 0.5;

	private static Box body(BlockPos pos) {
		return new Box(
				pos.getX() + 0.5 - PLAYER_HALF_WIDTH, pos.getY(),
				pos.getZ() + 0.5 - PLAYER_HALF_WIDTH,
				pos.getX() + 0.5 + PLAYER_HALF_WIDTH, pos.getY() + 1.0,
				pos.getZ() + 0.5 + PLAYER_HALF_WIDTH);
	}

	private static boolean solid(ServerWorld world, BlockPos pos) {
		BlockState state = stateOf(world, pos);
		return state != null && state.blocksMovement();
	}

	private static boolean blocked(ServerWorld world, BlockPos pos) {
		return !free(world, pos);
	}

	private static BlockState stateOf(ServerWorld world, BlockPos pos) {
		if (world.isOutOfHeightLimit(pos.getY())) {
			return null;
		}

		WorldChunk chunk = world.getChunkManager()
				.getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);

		return chunk == null ? null : chunk.getBlockState(pos);
	}

	private static BlockPos groundAt(ServerWorld world, BlockPos pos) {
		if (occupiable(world, pos)) {
			return pos;
		}

		for (int drop = 1; drop <= MAX_FALL + 1; drop++) {
			BlockPos below = pos.down(drop);

			if (occupiable(world, below)) {
				return below;
			}

			//
			if (solid(world, below)) {
				return null;
			}
		}

		return null;
	}

	private static BlockPos nearestStanding(ServerWorld world, BlockPos around) {
		BlockPos afloat = null;

		for (int radius = 1; radius <= 4; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -radius; dy <= radius; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						BlockPos candidate = around.add(dx, dy, dz);

						if (canStand(world, candidate)) {
							return candidate;
						}

						if (afloat == null && canSwim(world, candidate)) {
							afloat = candidate;
						}
					}
				}
			}
		}

		return afloat;
	}

	private static double heuristic(BlockPos from, BlockPos to) {
		int dx = Math.abs(from.getX() - to.getX());
		int dz = Math.abs(from.getZ() - to.getZ());
		int dy = Math.abs(from.getY() - to.getY());

		return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + dy * 0.5;
	}

	private static final int MAX_PATH_NODES = MAX_NODES;

	private static List<BlockPos> trace(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
		List<BlockPos> path = new ArrayList<>();
		BlockPos current = end;

		while (current != null && path.size() < MAX_PATH_NODES) {
			path.add(0, current);
			current = cameFrom.get(current);
		}

		return path;
	}

	private static List<BlockPos> route(ServerWorld world, Map<BlockPos, BlockPos> cameFrom,
			BlockPos end) {
		return tighten(world, straighten(world, simplify(trace(cameFrom, end))));
	}

	private static final double COMFORT = 0.12;

	private static final double AIM_STEP = 0.04;

	public static Vec3d aim(ServerWorld world, BlockPos cell) {
		VoxelShape shape = obstruction(world, cell);

		if (shape == null) {
			return Vec3d.ofBottomCenter(cell);
		}

		double bestX = 0.5;
		double bestZ = 0.5;
		double bestClearance = -1.0;

		double from = PLAYER_HALF_WIDTH;
		double to = 1.0 - PLAYER_HALF_WIDTH;

		for (double x = from; x <= to + 1.0e-9; x += AIM_STEP) {
			for (double z = from; z <= to + 1.0e-9; z += AIM_STEP) {
				double clearance = clearance(shape, cell, x, z);

				if (clearance < 0.0) {
					continue;
				}

				if (clearance > bestClearance + 1.0e-9
						|| (Math.abs(clearance - bestClearance) < 1.0e-9
								&& distanceToCentre(x, z) < distanceToCentre(bestX, bestZ))) {
					bestClearance = clearance;
					bestX = x;
					bestZ = z;
				}
			}
		}

		return new Vec3d(cell.getX() + bestX, cell.getY(), cell.getZ() + bestZ);
	}

	private static double distanceToCentre(double x, double z) {
		return Math.abs(x - 0.5) + Math.abs(z - 0.5);
	}

	private static double clearance(VoxelShape shape, BlockPos cell, double x, double z) {
		for (double margin = COMFORT; margin > 1.0e-9; margin -= 0.04) {
			if (!touches(shape, cell, x, z, margin)) {
				return margin;
			}
		}

		return touches(shape, cell, x, z, 0.0) ? -1.0 : 0.0;
	}

	private static boolean touches(VoxelShape shape, BlockPos cell, double x, double z,
			double margin) {
		Box box = new Box(
				cell.getX() + x - PLAYER_HALF_WIDTH - margin, cell.getY(),
				cell.getZ() + z - PLAYER_HALF_WIDTH - margin,
				cell.getX() + x + PLAYER_HALF_WIDTH + margin, cell.getY() + 1.0,
				cell.getZ() + z + PLAYER_HALF_WIDTH + margin);

		return VoxelShapes.matchesAnywhere(shape, VoxelShapes.cuboid(box), BooleanBiFunction.AND);
	}

	private static VoxelShape obstruction(ServerWorld world, BlockPos cell) {
		BlockState state = stateOf(world, cell);

		if (state == null || !state.blocksMovement() || state.isFullCube(world, cell)) {
			return null;
		}

		VoxelShape shape = state.getCollisionShape(world, cell);

		return shape.isEmpty()
				? null
				: shape.offset(cell.getX(), cell.getY(), cell.getZ());
	}

	private static List<BlockPos> tighten(ServerWorld world, List<BlockPos> path) {
		if (path.size() < 2) {
			return path;
		}

		List<BlockPos> tightened = new ArrayList<>(path.size() + 2);

		for (int i = 0; i < path.size() - 1; i++) {
			tightened.add(path.get(i));

			for (BlockPos cell : between(path.get(i), path.get(i + 1))) {
				if (obstruction(world, cell) != null && occupiable(world, cell)
						&& !tightened.contains(cell)) {
					tightened.add(cell);
				}
			}
		}

		tightened.add(path.get(path.size() - 1));
		return tightened;
	}

	public static String probe(ServerWorld world, BlockPos pos) {
		BlockState state = stateOf(world, pos);
		BlockState above = stateOf(world, pos.up());

		return String.format(
				"%s: блок=%s, над=%s%n"
						+ "  ноги свободны (с переступанием): %s%n"
						+ "  голова свободна в полный рост:   %s%n"
						+ "  голова свободна пригнувшись:     %s%n"
						+ "  опора под ногами:                %s%n"
						+ "  => встать: %s, пригнуться: %s, вплавь: %s",
				pos.toShortString(),
				state == null ? "нет чанка" : state.getBlock().getName().getString(),
				above == null ? "нет чанка" : above.getBlock().getName().getString(),
				free(world, pos, STEP_OVER), free(world, pos.up()),
				free(world, pos.up(), 0.0, CROUCH_HEAD), solid(world, pos.down()),
				canStand(world, pos), canCrouch(world, pos), canSwim(world, pos));
	}

	public static boolean crouchAlong(ServerWorld world, BlockPos from, BlockPos to) {
		if (crouchOnly(world, to)) {
			return true;
		}

		for (BlockPos cell : between(from, to)) {
			if (crouchOnly(world, cell)) {
				return true;
			}
		}

		return false;
	}

	private static List<BlockPos> between(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double dz = to.getZ() - from.getZ();
		double length = Math.max(Math.abs(dx), Math.abs(dz));

		if (length < 1.0) {
			return List.of();
		}

		List<BlockPos> cells = new ArrayList<>();
		int samples = (int) Math.ceil(length / LINE_STEP);

		for (int step = 1; step < samples; step++) {
			double t = (double) step / samples;
			BlockPos cell = BlockPos.ofFloored(
					from.getX() + 0.5 + dx * t,
					from.getY() + dy * t + 0.001,
					from.getZ() + 0.5 + dz * t);

			if (!cell.equals(from) && !cell.equals(to) && !cells.contains(cell)) {
				cells.add(cell);
			}
		}

		return cells;
	}

	private static final int STRAIGHTEN_SPAN = 12;

	private static List<BlockPos> straighten(ServerWorld world, List<BlockPos> path) {
		if (path.size() < 3) {
			return path;
		}

		List<BlockPos> straight = new ArrayList<>();
		straight.add(path.get(0));

		int i = 0;

		while (i < path.size() - 1) {
			int next = i + 1;
			int limit = Math.min(path.size() - 1, i + STRAIGHTEN_SPAN);

			for (int j = limit; j > i + 1; j--) {
				if (mergeable(world, path.get(i), path.get(j))) {
					next = j;
					break;
				}
			}

			straight.add(path.get(next));
			i = next;
		}

		return straight;
	}

	private static final double LINE_STEP = 0.25;

	private static boolean clearLine(ServerWorld world, BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);

		if (length == 0) {
			return true;
		}

		int samples = (int) Math.ceil(length / LINE_STEP);

		double sideX = -dz / length * PLAYER_HALF_WIDTH;
		double sideZ = dx / length * PLAYER_HALF_WIDTH;

		for (int step = 0; step <= samples; step++) {
			double t = (double) step / samples;
			double x = from.getX() + 0.5 + dx * t;
			double z = from.getZ() + 0.5 + dz * t;

			for (int side = -1; side <= 1; side++) {
				BlockPos cell = BlockPos.ofFloored(
						x + sideX * side, from.getY(), z + sideZ * side);

				if (!canStand(world, cell) || water(world, cell)) {
					return false;
				}
			}
		}

		return true;
	}

	private static final double PLAYER_HALF_WIDTH = 0.31;

	private static boolean mergeable(ServerWorld world, BlockPos from, BlockPos to) {
		if (to.getY() == from.getY()) {
			return clearLine(world, from, to);
		}

		return to.getY() < from.getY() && clearDescent(world, from, to);
	}

	private static boolean clearDescent(ServerWorld world, BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);

		if (length == 0) {
			return false;
		}

		int stepX = (int) Math.signum(dx);
		int stepZ = (int) Math.signum(dz);

		double sideX = -dz / length * PLAYER_HALF_WIDTH;
		double sideZ = dx / length * PLAYER_HALF_WIDTH;

		int samples = (int) Math.ceil(length / LINE_STEP);
		int y = from.getY();
		BlockPos previous = from;

		for (int step = 0; step <= samples; step++) {
			double t = (double) step / samples;
			int x = (int) Math.floor(from.getX() + 0.5 + dx * t);
			int z = (int) Math.floor(from.getZ() + 0.5 + dz * t);

			BlockPos cell = new BlockPos(x, y, z);

			if (cell.getX() == previous.getX() && cell.getZ() == previous.getZ()) {
				continue;
			}

			int landing = floorUnder(world, cell, Math.max(to.getY(), y - MAX_FALL), y);

			if (landing == Integer.MIN_VALUE || landing > y) {
				return false;
			}

			int drop = y - landing;

			if (drop > 1 && !lands(world, new BlockPos(x, landing, z), stepX, stepZ)) {
				return false;
			}

			y = landing;
			previous = new BlockPos(x, y, z);

			for (int side = -1; side <= 1; side += 2) {
				BlockPos shoulder = BlockPos.ofFloored(
						from.getX() + 0.5 + dx * t + sideX * side, y,
						from.getZ() + 0.5 + dz * t + sideZ * side);

				if (blocked(world, shoulder) || blocked(world, shoulder.up())) {
					return false;
				}
			}
		}

		return y == to.getY();
	}

	private static int floorUnder(ServerWorld world, BlockPos column, int floor, int ceiling) {
		for (int y = ceiling; y >= floor; y--) {
			BlockPos cell = new BlockPos(column.getX(), y, column.getZ());

			if (water(world, cell)) {
				return Integer.MIN_VALUE;
			}

			if (canStand(world, cell)) {
				return y;
			}

			if (!free(world, cell)) {
				return Integer.MIN_VALUE;
			}
		}

		return Integer.MIN_VALUE;
	}

	private static List<BlockPos> simplify(List<BlockPos> path) {
		if (path.size() < 3) {
			return path;
		}

		List<BlockPos> simplified = new ArrayList<>();
		simplified.add(path.get(0));

		for (int i = 1; i < path.size() - 1; i++) {
			BlockPos previous = path.get(i - 1);
			BlockPos current = path.get(i);
			BlockPos next = path.get(i + 1);

			int dx1 = current.getX() - previous.getX();
			int dy1 = current.getY() - previous.getY();
			int dz1 = current.getZ() - previous.getZ();

			int dx2 = next.getX() - current.getX();
			int dy2 = next.getY() - current.getY();
			int dz2 = next.getZ() - current.getZ();

			//
			//
			boolean climbs = dy1 > 0 || dy2 > 0;

			if (climbs || dx1 != dx2 || dy1 != dy2 || dz1 != dz2) {
				simplified.add(current);
			}
		}

		simplified.add(path.get(path.size() - 1));
		return simplified;
	}
}
