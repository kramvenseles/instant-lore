package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.camera.CameraEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void instantlore$scaleFov(Camera camera, float tickDelta, boolean changingFov,
			CallbackInfoReturnable<Float> info) {
		float multiplier = CameraEffects.fovMultiplier();

		if (multiplier != 1.0F) {
			info.setReturnValue(info.getReturnValue() * multiplier);
		}
	}
}
