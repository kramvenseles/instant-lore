package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetPredicate.class)
public class TargetPredicateMixin {

	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideTarget(ServerWorld world, LivingEntity tester, LivingEntity target,
			CallbackInfoReturnable<Boolean> cir) {
		if (InvisibleModule.untargetable(target)) {
			cir.setReturnValue(false);
		}
	}
}
