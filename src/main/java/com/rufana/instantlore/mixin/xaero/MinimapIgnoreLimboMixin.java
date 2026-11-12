package com.rufana.instantlore.mixin.xaero;

import com.rufana.instantlore.client.compat.XaeroLimboGate;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.common.minimap.write.MinimapWriter", remap = false)
public class MinimapIgnoreLimboMixin {
	@Inject(method = "ignoreWorld", at = @At("HEAD"), cancellable = true, remap = false)
	private void instantlore$ignoreLimbo(World world, CallbackInfoReturnable<Boolean> cir) {
		if (XaeroLimboGate.isLimbo(world)) {
			cir.setReturnValue(true);
		}
	}
}
