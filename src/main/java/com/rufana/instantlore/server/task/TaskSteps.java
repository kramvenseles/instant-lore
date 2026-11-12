package com.rufana.instantlore.server.task;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.PlayerEvents;
import com.rufana.instantlore.server.path.LorePathfinder;
import com.rufana.instantlore.server.path.PathRunner;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.atomic.AtomicReference;

public final class TaskSteps {
	private TaskSteps() {
	}

	public static TaskStep goTo(BlockPos goal, String speed, double timeout) {
		return new TaskStep() {
			private final AtomicReference<Boolean> done = new AtomicReference<>();

			@Override
			public String describe() {
				return "дойти до " + goal.toShortString();
			}

			@Override
			public void start(ServerPlayerEntity player) {
				PathRunner.walkTo(player, goal, speed, (reached, why) -> done.set(reached));
			}

			@Override
			public boolean onTick(ServerPlayerEntity player) {
				Boolean result = done.get();

				if (result == null) {
					return false;
				}

				if (!result) {
					throw new TaskFailure("дорога не удалась");
				}

				return true;
			}

			@Override
			public double timeoutSeconds() {
				return timeout;
			}
		};
	}

	public static TaskStep lookAt(double x, double y, double z) {
		return new TaskStep() {
			@Override
			public String describe() {
				return "посмотреть на " + x + " " + y + " " + z;
			}

			@Override
			public void start(ServerPlayerEntity player) {
				PlayerEvents.control(player, 30);
				PlayerEvents.lookAt(player, x, y, z);
			}

			@Override
			public boolean onReport(ServerPlayerEntity player, String type, JsonObject data) {
				return "player.looked".equals(type);
			}

			@Override
			public double timeoutSeconds() {
				return 10;
			}
		};
	}

	public static TaskStep breakBlock(BlockPos target, double timeout) {
		return new TaskStep() {
			private static final int RENEW_TICKS = 20 * 15;

			private int ticks;
			private boolean walking = true;
			private final AtomicReference<String> walkResult = new AtomicReference<>();

			@Override
			public String describe() {
				return "сломать блок " + target.toShortString();
			}

			@Override
			public void start(ServerPlayerEntity player) {
				BlockPos stand = standingSpotNear(player, target);

				if (stand == null) {
					throw new TaskFailure("негде встать рядом с блоком");
				}

				PathRunner.walkTo(player, stand, "walk",
						(reached, why) -> walkResult.set(reached ? "" : why));
			}

			@Override
			public boolean onTick(ServerPlayerEntity player) {
				if (walking) {
					String result = walkResult.get();

					if (result == null) {
						return false;
					}

					if (!result.isEmpty()) {
						throw new TaskFailure("не дошёл до блока: " + result);
					}

					walking = false;
					ticks = 0;
					aim(player);
					return false;
				}

				if (player.getEntityWorld().getBlockState(target).isAir()) {
					PlayerEvents.send(player, "player.unhold", "{\"button\":\"left\"}");
					return true;
				}

				if (++ticks % RENEW_TICKS == 0) {
					aim(player);
				}

				return false;
			}

			private void aim(ServerPlayerEntity player) {
				PlayerEvents.control(player, 60);
				PlayerEvents.lookAt(player,
						target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
				PlayerEvents.hold(player, "left", 30);
			}

			@Override
			public double timeoutSeconds() {
				return timeout;
			}
		};
	}

	public static TaskStep click(String button, int count) {
		return new TaskStep() {
			@Override
			public String describe() {
				return "нажать " + button + " ×" + count;
			}

			@Override
			public void start(ServerPlayerEntity player) {
				PlayerEvents.control(player, 30);
				PlayerEvents.click(player, button, count);
			}

			@Override
			public boolean onTick(ServerPlayerEntity player) {
				return true;
			}

			@Override
			public double timeoutSeconds() {
				return 5;
			}
		};
	}

	public static TaskStep wait(double seconds) {
		return new TaskStep() {
			@Override
			public String describe() {
				return "подождать " + seconds + " с";
			}

			@Override
			public void start(ServerPlayerEntity player) {
			}

			@Override
			public double timeoutSeconds() {
				return seconds;
			}

			@Override
			public boolean failsOnTimeout() {
				return false;
			}
		};
	}

	public static TaskStep say(String message) {
		return new TaskStep() {
			@Override
			public String describe() {
				return "сообщение: " + message;
			}

			@Override
			public void start(ServerPlayerEntity player) {
				player.sendMessage(Text.literal(message), false);
			}

			@Override
			public boolean onTick(ServerPlayerEntity player) {
				return true;
			}

			@Override
			public double timeoutSeconds() {
				return 5;
			}
		};
	}

	private static BlockPos standingSpotNear(ServerPlayerEntity player, BlockPos target) {
		int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dy : new int[] {0, 1, -1, 2, -2}) {
			for (int[] offset : offsets) {
				BlockPos candidate = target.add(offset[0], dy, offset[1]);

				if (!LorePathfinder.canStandAt(player.getEntityWorld(), candidate)) {
					continue;
				}

				double distance = candidate.getSquaredDistance(player.getBlockPos());

				if (distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
		}

		if (best == null) {
			InstantLore.LOGGER.warn("No spot near {} from which to reach the block", target);
		}

		return best;
	}
}
