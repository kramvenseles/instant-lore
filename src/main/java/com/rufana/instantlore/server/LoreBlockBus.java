package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreBlockDefinition;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoreBlockBus {
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static final List<UseListener> USE_LISTENERS = new CopyOnWriteArrayList<>();

	private LoreBlockBus() {
	}

	@FunctionalInterface
	public interface Listener {
		void onTick(ServerWorld world, BlockPos pos, int slot, LoreBlockDefinition definition);
	}

	@FunctionalInterface
	public interface UseListener {
		boolean onUse(ServerPlayerEntity player, Hand hand, ItemStack stack, ServerWorld world,
				BlockPos pos, int slot, LoreBlockDefinition definition, Direction side,
				Vec3d hit, boolean sneaking);
	}

	public static void subscribe(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void subscribeUse(UseListener listener) {
		USE_LISTENERS.add(listener);
	}

	public static void tick(ServerWorld world, BlockPos pos, int slot, LoreBlockDefinition definition) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onTick(world, pos, slot, definition);
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Block tick handler for {} failed", slot, e);
			}
		}
	}

	public static boolean use(ServerPlayerEntity player, Hand hand, ItemStack stack, ServerWorld world,
			BlockPos pos, int slot, LoreBlockDefinition definition, Direction side,
			Vec3d hit, boolean sneaking) {
		for (UseListener listener : USE_LISTENERS) {
			try {
				if (listener.onUse(player, hand, stack, world, pos, slot, definition, side, hit, sneaking)) {
					return true;
				}
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Right-click handler for block {} failed", slot, e);
			}
		}
		return false;
	}
}
