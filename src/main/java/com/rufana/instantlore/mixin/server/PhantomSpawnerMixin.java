package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

	@Redirect(method = "spawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z"))
	private boolean instantlore$skipHidden(ServerPlayerEntity player) {
		return player.isSpectator() || InvisibleModule.skippedBySpawning(player);
	}
}
