package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.waypoint.ServerWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWaypoint.class)
public interface ServerWaypointMixin {

	@Inject(method = "cannotReceive", at = @At("HEAD"), cancellable = true)
	private static void instantlore$hideFromLocator(LivingEntity entity, ServerPlayerEntity viewer,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof ServerPlayerEntity tracked
				&& tracked != viewer
				&& InvisibleModule.isHidden(tracked.getUuid())) {
			cir.setReturnValue(true);
		}
	}
}
