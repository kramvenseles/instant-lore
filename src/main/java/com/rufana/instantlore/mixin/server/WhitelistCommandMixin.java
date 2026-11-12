package com.rufana.instantlore.mixin.server;

import com.mojang.brigadier.CommandDispatcher;
import com.rufana.instantlore.server.WhitelistManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.dedicated.command.WhitelistCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WhitelistCommand.class)
public class WhitelistCommandMixin {

	@Inject(method = "register", at = @At("HEAD"), cancellable = true)
	private static void instantlore$replaceWhitelist(
			CommandDispatcher<ServerCommandSource> dispatcher, CallbackInfo ci) {
		WhitelistManager.register(dispatcher);
		ci.cancel();
	}
}
