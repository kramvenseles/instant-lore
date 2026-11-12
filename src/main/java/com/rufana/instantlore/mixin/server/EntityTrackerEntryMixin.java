package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {

	@Shadow
	@Final
	private Entity entity;

	@Inject(method = "startTracking", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideInvisible(ServerPlayerEntity viewer, CallbackInfo ci) {
		if (entity instanceof ServerPlayerEntity tracked
				&& tracked != viewer
				&& InvisibleModule.concealedFromViewer(viewer, tracked.getUuid())) {
			ci.cancel();
		}
	}
}
