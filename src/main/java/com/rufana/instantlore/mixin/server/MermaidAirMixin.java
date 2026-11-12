package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.MermaidModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MermaidAirMixin {

	@Inject(method = "getNextAirUnderwater", at = @At("HEAD"), cancellable = true)
	private void instantlore$mermaidBreathesUnderwater(int air, CallbackInfoReturnable<Integer> cir) {
		if (!((Object) this instanceof ServerPlayerEntity player)) {
			return;
		}
		if (!MermaidModule.appliesTo(player.getUuid())) {
			return;
		}
		cir.setReturnValue(player.getMaxAir());
	}

	@Inject(method = "getNextAirOnLand", at = @At("HEAD"), cancellable = true)
	private void instantlore$mermaidSurfaceDrowns(int air, CallbackInfoReturnable<Integer> cir) {
		if (!((Object) this instanceof ServerPlayerEntity player)) {
			return;
		}
		if (!MermaidModule.appliesTo(player.getUuid())) {
			return;
		}
		if (!(player.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		//
		if (!player.isTouchingWater() && !world.hasRain(player.getBlockPos())) {
			cir.setReturnValue(air - 1);
		}
	}
}
