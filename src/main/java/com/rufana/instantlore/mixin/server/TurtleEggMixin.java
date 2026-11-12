package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TurtleEggBlock.class)
public class TurtleEggMixin {

	@Inject(method = "tryBreakEgg", at = @At("HEAD"), cancellable = true)
	private void instantlore$noBreak(World world, BlockState state, BlockPos pos, Entity entity,
			int inverseChance, CallbackInfo ci) {
		if (InvisibleModule.blockStepHidden(entity)) {
			ci.cancel();
		}
	}
}
