package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.LimboGate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerTeleportGateMixin {

	@Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)"
			+ "Lnet/minecraft/server/network/ServerPlayerEntity;",
			at = @At("HEAD"), cancellable = true)
	private void instantlore$guardLimboBorder(TeleportTarget target,
			CallbackInfoReturnable<ServerPlayerEntity> cir) {
		ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
		if (target != null && target.world() != null
				&& LimboGate.blocked(self, target.world().getRegistryKey())) {
			cir.setReturnValue(self);
		}
	}
}
