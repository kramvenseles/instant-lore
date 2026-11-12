package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.StickyRide;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStopRidingMixin {

	@Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
	private void instantlore$keepStickyRide(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayerEntity player
				&& StickyRide.holds(player.getUuid())) {
			ci.cancel();
		}
	}
}
