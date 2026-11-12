package com.rufana.instantlore.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.rufana.instantlore.client.render.LoreFog;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
	private void instantlore$fogColor(Camera camera, float tickDelta, ClientWorld world,
			int viewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
		if (!LoreFog.isActive()) {
			return;
		}

		int c = LoreFog.color();
		float r = ((c >> 16) & 0xFF) / 255.0F;
		float g = ((c >> 8) & 0xFF) / 255.0F;
		float b = (c & 0xFF) / 255.0F;
		cir.setReturnValue(new Vector4f(r, g, b, 1.0F));
	}

	@Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
			at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
	private void instantlore$fogDistance(Camera camera, int viewDistance, RenderTickCounter tickCounter,
			float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir, @Local FogData data) {
		if (!LoreFog.isActive()) {
			return;
		}

		float start = LoreFog.start();
		float end = LoreFog.end();

		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = start;
		data.renderDistanceEnd = end;
		data.skyEnd = end;
		data.cloudEnd = end;
	}
}
