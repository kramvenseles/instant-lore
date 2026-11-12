package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.LimboTransfer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionImpl.class)
public class ExplosionLimboMixin {
	@Shadow
	@Final
	private ServerWorld world;

	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void instantlore$noLimboBlast(CallbackInfoReturnable<Integer> cir) {
		if (world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY)) {
			cir.setReturnValue(0);
		}
	}
}
