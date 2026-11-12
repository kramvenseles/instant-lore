package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.control.PlayerControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerControlEntityMixin {
	private boolean instantlore$isControlledSelf() {
		return (Object) this == MinecraftClient.getInstance().player;
	}

	@Inject(method = "setVelocityClient", at = @At("HEAD"), cancellable = true)
	private void instantlore$ignoreExternalVelocity(net.minecraft.util.math.Vec3d velocity,
			CallbackInfo ci) {
		if (instantlore$isControlledSelf() && PlayerControl.ignoresExternalVelocity()) {
			ci.cancel();
		}
	}

	@Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
	private void instantlore$ignoreEntityPush(Entity other, CallbackInfo ci) {
		if (instantlore$isControlledSelf() && PlayerControl.blocksEntityPush()) {
			ci.cancel();
		}
	}

	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void instantlore$lockLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if (instantlore$isControlledSelf() && PlayerControl.isLookLocked()) {
			ci.cancel();
		}
	}
}
