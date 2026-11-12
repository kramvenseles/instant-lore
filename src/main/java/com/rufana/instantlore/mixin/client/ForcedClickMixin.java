package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.control.ClickControl;
import com.rufana.instantlore.client.control.PlayerControl;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ForcedClickMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void instantlore$driveClicks(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;

		if (client.player == null || !PlayerControl.isActive()) {
			return;
		}

		if (client.currentScreen == null && client.mouse.isCursorLocked()) {
			return;
		}

		MinecraftClientInvoker invoker = (MinecraftClientInvoker) client;

		invoker.instantlore$handleBlockBreaking(ClickControl.isHeld(true));

		if (ClickControl.consumeClick(true)) {
			invoker.instantlore$doAttack();
		}

		if (ClickControl.isHeld(false) || ClickControl.consumeClick(false)) {
			invoker.instantlore$doItemUse();
		}
	}
}
