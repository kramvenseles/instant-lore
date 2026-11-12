package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.Desaturate;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererCameraEntityMixin {

	@Inject(method = "onCameraEntitySet", at = @At("RETURN"))
	private void instantlore$keepLimboDesaturate(Entity entity, CallbackInfo ci) {
		Desaturate.restoreIfLost();
	}
}
