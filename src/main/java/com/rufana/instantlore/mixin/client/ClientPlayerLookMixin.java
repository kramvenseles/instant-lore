package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.control.PlayerControl;
import com.rufana.instantlore.content.RideTurn;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerLookMixin {
	private boolean instantlore$isSelf() {
		return (Object) this == MinecraftClient.getInstance().player
				&& PlayerControl.isSmoothingLook();
	}

	@Inject(method = "getYaw(F)F", at = @At("HEAD"), cancellable = true)
	private void instantlore$smoothYaw(float tickDelta, CallbackInfoReturnable<Float> info) {
		if (instantlore$isSelf()) {
			info.setReturnValue(PlayerControl.smoothedYaw(tickDelta));
			return;
		}

		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;

		if (self != MinecraftClient.getInstance().player) {
			return;
		}

		float backlog = RideTurn.backlog(self.getId(), tickDelta);

		if (backlog != 0.0F) {
			info.setReturnValue(self.getYaw() - backlog);
		}
	}

	@Inject(method = "getPitch(F)F", at = @At("HEAD"), cancellable = true)
	private void instantlore$smoothPitch(float tickDelta, CallbackInfoReturnable<Float> info) {
		if (instantlore$isSelf()) {
			info.setReturnValue(PlayerControl.smoothedPitch(tickDelta));
		}
	}
}
