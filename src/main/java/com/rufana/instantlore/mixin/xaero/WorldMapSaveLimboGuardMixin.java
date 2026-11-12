package com.rufana.instantlore.mixin.xaero;

import com.rufana.instantlore.client.compat.XaeroLeakDetector;
import com.rufana.instantlore.client.compat.XaeroLimboGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.map.file.MapSaveLoad", remap = false)
public class WorldMapSaveLimboGuardMixin {
	@Inject(method = "saveRegion", at = @At("HEAD"), cancellable = true, remap = false)
	private void instantlore$skipLimboSave(@Coerce Object region, boolean fullSave, int level,
			CallbackInfoReturnable<Boolean> cir) {
		if (XaeroLimboGate.regionDestIsLimbo(region)) {
			XaeroLeakDetector.recordSaveBlock();
			cir.setReturnValue(false);
		}
	}
}
