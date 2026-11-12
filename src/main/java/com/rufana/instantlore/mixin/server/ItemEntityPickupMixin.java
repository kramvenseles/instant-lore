package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityPickupMixin {

	@Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
	private void instantlore$hidePickup(PlayerEntity player, CallbackInfo ci) {
		if (InvisibleModule.pickupHidden(player)) {
			ci.cancel();
		}
	}
}
