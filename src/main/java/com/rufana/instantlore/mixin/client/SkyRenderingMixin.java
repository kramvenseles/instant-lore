package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.LoreSky;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void instantlore$flattenSky(ClientWorld world, float tickDelta, Camera camera,
			SkyRenderState state, CallbackInfo ci) {
		if (LoreSky.isHidden()) {
			state.skybox = DimensionType.Skybox.NONE;
		}
	}

	@Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideCelestial(CallbackInfo ci) {
		if (LoreSky.isHidden()) {
			ci.cancel();
		}
	}
}
