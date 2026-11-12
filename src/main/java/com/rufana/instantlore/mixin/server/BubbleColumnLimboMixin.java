package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.LimboTransfer;
import net.minecraft.block.BlockState;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnLimboMixin {

	@Inject(method = "update(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;"
			+ "Lnet/minecraft/block/BlockState;)V", at = @At("HEAD"), cancellable = true)
	private static void instantlore$noColumnInLimbo(WorldAccess world, BlockPos pos,
			BlockState state, CallbackInfo ci) {
		if (instantlore$isLimbo(world)) {
			ci.cancel();
		}
	}

	@Inject(method = "update(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;"
			+ "Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)V",
			at = @At("HEAD"), cancellable = true)
	private static void instantlore$noColumnInLimbo(WorldAccess world, BlockPos pos,
			BlockState state, BlockState stateAbove, CallbackInfo ci) {
		if (instantlore$isLimbo(world)) {
			ci.cancel();
		}
	}

	private static boolean instantlore$isLimbo(WorldAccess world) {
		return world instanceof World w && w.getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
	}
}
