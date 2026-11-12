package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(MinecraftServer.class)
public class MinecraftServerPingMixin {

	@Redirect(method = "createMetadataPlayers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;"))
	private List<ServerPlayerEntity> instantlore$filterPing(PlayerManager manager) {
		return InvisibleModule.filterVisible(manager.getPlayerList());
	}
}
