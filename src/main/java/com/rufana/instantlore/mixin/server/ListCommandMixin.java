package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ListCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Function;

@Mixin(ListCommand.class)
public class ListCommandMixin {

	@Redirect(method = "execute(Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/function/Function;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;"))
	private static List<ServerPlayerEntity> instantlore$hideInvisible(
			PlayerManager manager, ServerCommandSource source, Function<ServerPlayerEntity, Text> nameProvider) {
		return InvisibleModule.filterListPlayers(source, manager.getPlayerList());
	}
}
