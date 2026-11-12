package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.LoreCoords;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenCoordsMixin {
	@Shadow
	protected String originalChatText;

	@Shadow
	protected boolean draft;

	@Inject(method = "<init>(Ljava/lang/String;Z)V", at = @At("TAIL"))
	private void instantlore$scrambleSuppliedCoords(String text, boolean draft, CallbackInfo ci) {
		if (!LoreCoords.isScrambled() || this.draft || text == null || text.isEmpty()) {
			return;
		}
		if (text.charAt(0) == '/' || !hasDigit(text)) {
			return;
		}
		this.originalChatText = LoreCoords.scrambleNumbers(text);
	}

	private static boolean hasDigit(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.isDigit(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}
}
