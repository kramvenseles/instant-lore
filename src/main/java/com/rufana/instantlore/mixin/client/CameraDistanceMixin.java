package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.LoreCamera;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class CameraDistanceMixin {
	@ModifyArg(
			method = "update",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),
			index = 0)
	private float instantlore$farther(float base) {
		float wanted = LoreCamera.distance();
		return wanted > 0.0F ? wanted : base;
	}
}
