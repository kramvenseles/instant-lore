package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityPushableMixin {

	@Inject(method = "isPushable", at = @At("RETURN"), cancellable = true)
	private void instantlore$notPushable(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && InvisibleModule.notPushable((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
