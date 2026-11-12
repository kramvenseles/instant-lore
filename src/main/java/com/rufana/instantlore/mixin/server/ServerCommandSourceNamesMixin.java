package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(ServerCommandSource.class)
public class ServerCommandSourceNamesMixin {

	@Inject(method = "getPlayerNames", at = @At("RETURN"), cancellable = true)
	private void instantlore$hideInvisibleNames(CallbackInfoReturnable<Collection<String>> cir) {
		Collection<String> filtered = InvisibleModule.filterPlayerNames(
				(ServerCommandSource) (Object) this, cir.getReturnValue());
		if (filtered != cir.getReturnValue()) {
			cir.setReturnValue(filtered);
		}
	}
}
