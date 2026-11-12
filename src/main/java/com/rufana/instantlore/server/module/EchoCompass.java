package com.rufana.instantlore.server.module;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LimboTransfer;
import com.rufana.instantlore.server.PlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.Optional;

public final class EchoCompass {

	private static final String MARK_KEY = "instantlore_echo";

	private static final String UNTIL_KEY = "instantlore_echo_until";

	private static final int HOLD_TICKS = 20 * 60;

	private static final int COOLDOWN_TICKS = 40;

	private static final int SCAN_INTERVAL_TICKS = 20;

	private static final int WANDER_EVERY_SCANS = 2;

	private static final int WANDER_RADIUS = 48;

	private static int ticks;
	private static int scans;

	private EchoCompass() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			try {
				onUse(player, world, hand);
			} catch (Throwable t) {
				InstantLore.LOGGER.error("Echo compass: apply failure", t);
			}

			return ActionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				onTick(server);
			} catch (Throwable t) {
				InstantLore.LOGGER.error("Echo compass: tick failure", t);
			}
		});
	}

	private static void onUse(net.minecraft.entity.player.PlayerEntity player, World world, Hand hand) {
		if (!(player instanceof ServerPlayerEntity sp)) {
			return;
		}

		ItemStack stack = sp.getStackInHand(hand);

		if (!isEchoCompass(stack) || !(sp.getEntityWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (!inLimbo(sw)) {
			sp.sendMessage(Text.literal("Стрелка спокойна: эхо звучит лишь по ту сторону.")
					.formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return;
		}

		long now = sw.getTime();

		long until = untilOf(stack);

		if (until > 0 && now < until - HOLD_TICKS + COOLDOWN_TICKS) {
			return;
		}

		ServerPlayerEntity target = nearestTarget(sp, sw);

		if (target == null) {
			wander(stack, sp, sw);
			setUntil(stack, 0L);
			sp.sendMessage(Text.literal("Эхо уходит в пустоту — рядом никого.")
					.formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
			playEcho(sp, 0.6F);
			return;
		}

		BlockPos at = target.getBlockPos();
		stack.set(DataComponentTypes.LODESTONE_TRACKER,
				new LodestoneTrackerComponent(Optional.of(GlobalPos.create(sw.getRegistryKey(), at)), false));
		setUntil(stack, now + HOLD_TICKS);

		double distance = Math.sqrt(sp.squaredDistanceTo(target));
		float pitch = (float) (1.4 - 0.7 * Math.min(1.0, distance / 512.0));

		playEcho(sp, pitch);
		sp.sendMessage(Text.literal("Эхо отзывается.")
				.formatted(Formatting.AQUA, Formatting.ITALIC), true);
	}

	private static void onTick(MinecraftServer server) {
		if (++ticks < SCAN_INTERVAL_TICKS) {
			return;
		}

		ticks = 0;
		boolean wanderNow = (++scans % WANDER_EVERY_SCANS) == 0;

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!(player.getEntityWorld() instanceof ServerWorld sw)) {
				continue;
			}

			for (Hand hand : Hand.values()) {
				ItemStack stack = player.getStackInHand(hand);

				if (!isEchoCompass(stack)) {
					continue;
				}

				if (!inLimbo(sw)) {
					if (stack.contains(DataComponentTypes.LODESTONE_TRACKER)) {
						stack.remove(DataComponentTypes.LODESTONE_TRACKER);
						setUntil(stack, 0L);
					}

					continue;
				}

				long until = untilOf(stack);

				if (until > 0 && sw.getTime() < until) {
					continue;
				}

				if (wanderNow) {
					wander(stack, player, sw);
				}
			}
		}
	}

	private static ServerPlayerEntity nearestTarget(ServerPlayerEntity self, ServerWorld world) {
		ServerPlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (ServerPlayerEntity other : world.getPlayers()) {
			if (other == self || !visible(other)) {
				continue;
			}

			double distance = self.squaredDistanceTo(other);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = other;
			}
		}

		return best;
	}

	private static boolean visible(ServerPlayerEntity player) {
		return !player.isSpectator()
				&& !InvisibleModule.isHidden(player.getUuid())
				&& !InvisibleModule.isVanished(player)
				&& !player.hasStatusEffect(StatusEffects.INVISIBILITY);
	}

	private static void wander(ItemStack stack, ServerPlayerEntity player, ServerWorld world) {
		double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
		int dx = (int) Math.round(Math.cos(angle) * WANDER_RADIUS);
		int dz = (int) Math.round(Math.sin(angle) * WANDER_RADIUS);
		BlockPos at = player.getBlockPos().add(dx, 0, dz);

		stack.set(DataComponentTypes.LODESTONE_TRACKER,
				new LodestoneTrackerComponent(Optional.of(GlobalPos.create(world.getRegistryKey(), at)), false));
	}

	private static void playEcho(ServerPlayerEntity player, float pitch) {
		PlayerEvents.send(player, "sound.play",
				"{\"id\":\"minecraft:block.amethyst_block.resonate\",\"volume\":0.7,\"pitch\":" + pitch + "}");
	}

	private static boolean isEchoCompass(ItemStack stack) {
		if (stack.isEmpty() || !stack.isOf(Items.COMPASS)) {
			return false;
		}

		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);

		return data != null && data.copyNbt().getBoolean(MARK_KEY, false);
	}

	private static boolean inLimbo(ServerWorld world) {
		return world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
	}

	private static long untilOf(ItemStack stack) {
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);

		return data == null ? 0L : data.copyNbt().getLong(UNTIL_KEY, 0L);
	}

	private static void setUntil(ItemStack stack, long until) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, (NbtCompound nbt) -> {
			nbt.putLong(UNTIL_KEY, until);
		});
	}
}
