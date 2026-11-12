package com.rufana.instantlore.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudAirBubblesMixin {

	@Inject(method = "renderAirBubbles", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideFullAir(DrawContext context, PlayerEntity player,
			int lastBurstBubble, int top, int x, CallbackInfo ci) {
		if (player.getAir() >= player.getMaxAir()) {
			ci.cancel();
		}
	}
}
