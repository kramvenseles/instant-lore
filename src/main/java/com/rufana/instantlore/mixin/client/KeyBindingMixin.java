package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.control.ClickControl;
import com.rufana.instantlore.client.cutscene.CutscenePlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
	@Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
	private void instantlore$blockWhilePlaying(CallbackInfoReturnable<Boolean> cir) {
		if (CutscenePlayer.isInputLocked()) {
			cir.setReturnValue(false);
			return;
		}

		Boolean forced = instantlore$mouseButton();

		if (forced != null) {
			cir.setReturnValue(ClickControl.isHeld(forced));
			return;
		}

		if (instantlore$gated()) {
			cir.setReturnValue(false);
		}
	}

	private boolean instantlore$gated() {
		return com.rufana.instantlore.client.control.KeyGate.isBlocked(
				((KeyBinding) (Object) this).getId());
	}

	@Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
	private void instantlore$blockQueuedWhilePlaying(CallbackInfoReturnable<Boolean> cir) {
		if (CutscenePlayer.isInputLocked()) {
			cir.setReturnValue(false);
			return;
		}

		Boolean forced = instantlore$mouseButton();

		if (forced != null) {
			cir.setReturnValue(ClickControl.consumeClick(forced));
			return;
		}

		if (instantlore$gated()) {
			cir.setReturnValue(false);
		}
	}

	private Boolean instantlore$mouseButton() {
		if (!ClickControl.isControlled()) {
			return null;
		}

		MinecraftClient client = MinecraftClient.getInstance();

		if (client == null || client.options == null) {
			return null;
		}

		if ((Object) this == client.options.attackKey) {
			return Boolean.TRUE;
		}

		if ((Object) this == client.options.useKey) {
			return Boolean.FALSE;
		}

		return null;
	}
}
