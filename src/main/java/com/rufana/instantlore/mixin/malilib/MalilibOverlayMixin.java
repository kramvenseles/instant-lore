package com.rufana.instantlore.mixin.malilib;

import com.rufana.instantlore.client.render.LoreOverlays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "fi.dy.masa.malilib.event.RenderEventHandler", remap = false)
public abstract class MalilibOverlayMixin {
	@Inject(method = "onRenderGameOverlayPost", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteOverlay(@Coerce Object ctx, float tickDelta, CallbackInfo ci) {
		if (LoreOverlays.isSuppressed()) {
			ci.cancel();
		}
	}

	@Inject(method = "onRenderTooltipLast", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteTooltip(@Coerce Object ctx, @Coerce Object stack, int x, int y,
			CallbackInfo ci) {
		if (LoreOverlays.isSuppressed()) {
			ci.cancel();
		}
	}

	@Inject(method = "runRenderWorldPreWeather", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteWorldPreWeather(@Coerce Object positionMatrix,
			@Coerce Object projectionMatrix, @Coerce Object client, @Coerce Object a,
			@Coerce Object b, @Coerce Object frustum, @Coerce Object camera, @Coerce Object c,
			@Coerce Object profiler, CallbackInfo ci) {
		if (LoreOverlays.isSuppressed()) {
			ci.cancel();
		}
	}

	@Inject(method = "runRenderWorldLast", at = @At("HEAD"), cancellable = true)
	private void instantlore$muteWorldLast(@Coerce Object positionMatrix,
			@Coerce Object projectionMatrix, @Coerce Object client, @Coerce Object a,
			@Coerce Object b, @Coerce Object frustum, @Coerce Object camera, @Coerce Object c,
			@Coerce Object profiler, CallbackInfo ci) {
		if (LoreOverlays.isSuppressed()) {
			ci.cancel();
		}
	}
}
