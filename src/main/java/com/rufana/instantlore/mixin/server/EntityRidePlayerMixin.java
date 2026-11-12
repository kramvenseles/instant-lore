package com.rufana.instantlore.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rufana.instantlore.server.StickyRide;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityRidePlayerMixin {

	@WrapOperation(method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/entity/EntityType;isSaveable()Z"))
	private boolean instantlore$allowRidingPlayer(EntityType<?> type, Operation<Boolean> original) {
		if (StickyRide.mounting()) {
			return true;
		}
		return original.call(type);
	}
}
