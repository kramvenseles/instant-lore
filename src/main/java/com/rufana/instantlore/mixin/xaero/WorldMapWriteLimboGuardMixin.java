package com.rufana.instantlore.mixin.xaero;

import com.rufana.instantlore.client.compat.XaeroLeakDetector;
import com.rufana.instantlore.client.compat.XaeroLimboGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.MapWriter", remap = false)
public class WorldMapWriteLimboGuardMixin {
	@Inject(method = "writeMap", at = @At("HEAD"), cancellable = true, remap = false)
	private void instantlore$skipLimboWrite(CallbackInfoReturnable<Boolean> cir) {
		if (XaeroLimboGate.destinationIsLimbo(this)) {
			XaeroLeakDetector.recordGuardBlock();
			cir.setReturnValue(false);
		}
	}
}
