package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LimboHail;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ChatHailMixin {

	@Inject(method = "onChatMessage", at = @At("HEAD"))
	private void instantlore$hail(ChatMessageC2SPacket packet, CallbackInfo ci) {
		try {
			ServerPlayerEntity sender = ((ServerPlayNetworkHandler) (Object) this).getPlayer();

			if (sender == null) {
				return;
			}

			MinecraftServer server = sender.getEntityWorld().getServer();
			String message = packet.chatMessage();

			if (server == null || message == null) {
				return;
			}

			server.execute(() -> LimboHail.onChat(sender, message));
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Limbo hail: chat parsing failed: {}", e.toString());
		}
	}
}
