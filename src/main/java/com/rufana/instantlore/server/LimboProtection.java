package com.rufana.instantlore.server;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;

public final class LimboProtection {
	private LimboProtection() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
			if (!world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY)) {
				return true;
			}
			return !state.isOf(Blocks.BLACK_STAINED_GLASS);
		});
	}
}
