package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldGameEventMixin {

	@Inject(method = "emitGameEvent(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/event/GameEvent$Emitter;)V",
			at = @At("HEAD"), cancellable = true)
	private void instantlore$hideVibration(RegistryEntry<GameEvent> event, Vec3d pos,
			GameEvent.Emitter emitter, CallbackInfo ci) {
		if (InvisibleModule.vibrationSuppressed(emitter.sourceEntity())) {
			ci.cancel();
		}
	}
}
