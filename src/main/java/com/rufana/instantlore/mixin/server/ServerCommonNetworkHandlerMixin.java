package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerCommonNetworkHandlerMixin {

	@ModifyVariable(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
			at = @At("HEAD"), argsOnly = true)
	private Packet<?> instantlore$filterTab(Packet<?> packet) {
		if ((Object) this instanceof ServerPlayNetworkHandler handler) {
			return InvisibleModule.filterPlayerList(packet, handler.getPlayer());
		}
		return packet;
	}
}
