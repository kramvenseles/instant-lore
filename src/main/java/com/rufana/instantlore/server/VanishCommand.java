package com.rufana.instantlore.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rufana.instantlore.server.module.InvisibleModule;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public final class VanishCommand {

	private VanishCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("vanish")
				.requires(Permissions.require("vanish.command.vanish", 2))
				.executes(ctx -> setSelf(ctx, !isSelfVanished(ctx)))
				.then(CommandManager.literal("on").executes(ctx -> setSelf(ctx, true)))
				.then(CommandManager.literal("off").executes(ctx -> setSelf(ctx, false)))
				.then(CommandManager.literal("reload")
						.requires(Permissions.require("vanish.command.vanish.reload", 2))
						.executes(VanishCommand::reload))
				.then(CommandManager.literal("players")
						.requires(Permissions.require("vanish.command.vanish.other", 2))
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> setOthers(ctx, null))
								.then(CommandManager.literal("on").executes(ctx -> setOthers(ctx, true)))
								.then(CommandManager.literal("off").executes(ctx -> setOthers(ctx, false))))));
	}

	private static boolean isSelfVanished(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		return player != null && InvisibleModule.isVanished(player);
	}

	private static int setSelf(CommandContext<ServerCommandSource> ctx, boolean vanished)
			throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		InvisibleModule.setVanished(player, vanished);
		player.sendMessage(Text.literal(vanished
				? "You are now vanished" : "You are no longer vanished"), false);
		return 1;
	}

	private static int setOthers(CommandContext<ServerCommandSource> ctx, Boolean explicit)
			throws CommandSyntaxException {
		Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
		ServerCommandSource source = ctx.getSource();
		int count = 0;
		for (ServerPlayerEntity target : targets) {
			boolean vanished = explicit != null ? explicit : !InvisibleModule.isVanished(target);
			InvisibleModule.setVanished(target, vanished);
			String msg = vanished
					? target.getName().getString() + " is now vanished"
					: target.getName().getString() + " is no longer vanished";
			com.rufana.instantlore.InstantLore.ilReply(source, () -> Text.literal(msg), false);
			target.sendMessage(Text.literal(vanished
					? "You are now vanished" : "You are no longer vanished"), false);
			count++;
		}
		return count;
	}

	private static int reload(CommandContext<ServerCommandSource> ctx) {
		com.rufana.instantlore.InstantLore.ilReply(ctx.getSource(),
				() -> Text.literal("Config has been reloaded!"), false);
		return 1;
	}
}
