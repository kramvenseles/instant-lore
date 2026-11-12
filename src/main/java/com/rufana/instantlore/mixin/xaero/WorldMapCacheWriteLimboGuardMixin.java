package com.rufana.instantlore.mixin.xaero;

import com.rufana.instantlore.client.compat.XaeroLeakDetector;
import com.rufana.instantlore.client.compat.XaeroLimboGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(targets = "xaero.map.region.LeveledRegion", remap = false)
public class WorldMapCacheWriteLimboGuardMixin {
	@Inject(method = "saveCacheTextures(Ljava/io/File;ZI)Z", at = @At("HEAD"),
			cancellable = true, remap = false)
	private void instantlore$skipLimboCache(File file, boolean log, int retries,
			CallbackInfoReturnable<Boolean> cir) {
		if (XaeroLimboGate.regionDestIsLimbo(this)) {
			XaeroLeakDetector.recordCacheBlock();
			cir.setReturnValue(false);
		}
	}
}
