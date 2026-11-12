package com.rufana.instantlore.mixin.carpet;

import com.rufana.instantlore.server.FakePlayerGuard;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "carpet.patches.EntityPlayerMPFake", remap = false)
public abstract class EntityPlayerMPFakeCreateMixin {

	@Inject(method = "createFake", at = @At("HEAD"), cancellable = true, remap = false)
	private static void instantlore$blockRealPlayerNames(String username, MinecraftServer server,
			Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId,
			GameMode gamemode, boolean flying, CallbackInfoReturnable<Boolean> cir) {
		if (FakePlayerGuard.hasRealPlayerData(server, username)) {
			FakePlayerGuard.logRefusedSpawn(username);
			cir.setReturnValue(false);
		}
	}
}
