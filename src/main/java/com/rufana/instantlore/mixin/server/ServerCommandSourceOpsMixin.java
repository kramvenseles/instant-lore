package com.rufana.instantlore.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rufana.instantlore.server.module.InvisibleModule;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceOpsMixin {

	@WrapOperation(method = "sendToOps",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerPlayerEntity;sendMessage(Lnet/minecraft/text/Text;)V"))
	private void instantlore$hideAdminEcho(ServerPlayerEntity op, Text message, Operation<Void> original) {
		try {
			ServerPlayerEntity subject = ((ServerCommandSource) (Object) this).getPlayer();
			if (subject != null && InvisibleModule.concealedFromViewer(op, subject.getUuid())) {
				return;
			}
		} catch (Throwable t) {
		}
		original.call(op, message);
	}
}
