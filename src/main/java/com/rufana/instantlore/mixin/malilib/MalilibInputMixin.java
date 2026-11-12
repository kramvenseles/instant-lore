package com.rufana.instantlore.mixin.malilib;

import com.rufana.instantlore.client.render.LoreOverlays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "fi.dy.masa.malilib.event.InputEventHandler", remap = false)
public abstract class MalilibInputMixin {
	@Inject(method = "onKeyInput", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteKeys(@Coerce Object input, int action, @Coerce Object client,
			CallbackInfoReturnable<Boolean> cir) {
		if (LoreOverlays.isSuppressed()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteClicks(@Coerce Object input, int action, @Coerce Object client,
			CallbackInfoReturnable<Boolean> cir) {
		if (LoreOverlays.isSuppressed()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteScroll(double x, double y, double horizontal, double vertical,
			@Coerce Object client, CallbackInfoReturnable<Boolean> cir) {
		if (LoreOverlays.isSuppressed()) {
			cir.setReturnValue(false);
		}
	}
}
