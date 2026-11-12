package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityInvulnerableMixin {

	@Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
	private void instantlore$vanishInvulnerable(ServerWorld world, DamageSource source,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayerEntity player && InvisibleModule.invulnerableFor(player)) {
			cir.setReturnValue(true);
		}
	}
}
