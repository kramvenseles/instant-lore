package com.rufana.instantlore.mixin.journeymap;

import com.rufana.instantlore.client.render.LoreCoords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "journeymap.client.ui.fullscreen.Fullscreen", remap = false)
public abstract class JourneyMapChatPosMixin {
	@ModifyVariable(method = "openChat", at = @At("HEAD"), argsOnly = true)
	private String instantlore$scrambleChatPosition(String text) {
		return LoreCoords.isScrambled() ? LoreCoords.scrambleNumbers(text) : text;
	}
}
