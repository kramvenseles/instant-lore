package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.FakePlayerGuard;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerSaveMixin {

	@Inject(method = "savePlayerData", at = @At("HEAD"), cancellable = true)
	private void instantlore$skipFakePlayerSave(ServerPlayerEntity player, CallbackInfo ci) {
		if (FakePlayerGuard.isCarpetFake(player)) {
			FakePlayerGuard.logBlockedSave(player);
			ci.cancel();
		}
	}
}
