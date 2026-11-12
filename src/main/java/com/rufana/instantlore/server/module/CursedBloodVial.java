package com.rufana.instantlore.server.module;

import com.rufana.instantlore.server.LoreItemBus;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CursedBloodVial {

	public static final String VIAL_ITEM = "cursed_blood_vial";

	private CursedBloodVial() {
	}

	public static void register() {
		LoreItemBus.subscribe((player, slot, definition, hand, pos) -> {
			if (!VIAL_ITEM.equals(definition.name())) {
				return;
			}
			onDrink(player);
		});
	}

	private static void onDrink(ServerPlayerEntity player) {
		boolean added = VampireModule.add(player);
		player.sendMessage(Text.literal(added
				? "Проклятая кровь растекается по жилам. Ты обращён — рассвет тебе больше не друг."
				: "Кровь уже течёт в тебе. Ты и так из тех, кто боится солнца.")
				.formatted(Formatting.DARK_RED), false);

		if (added) {
			announce(player);
		}
	}

	private static void announce(ServerPlayerEntity player) {
		MinecraftServer server = player.getEntityWorld().getServer();
		if (server == null) {
			return;
		}
		String name = player.getName().getString();

		var fade = new TitleFadeS2CPacket(10, 70, 20);
		var title = new TitleS2CPacket(
				Text.literal(name + " стал вампиром").formatted(Formatting.DARK_RED, Formatting.BOLD));
		var subtitle = new SubtitleS2CPacket(
				Text.literal("Кровь нашла нового хозяина").formatted(Formatting.GRAY));

		server.getPlayerManager().sendToAll(fade);
		server.getPlayerManager().sendToAll(title);
		server.getPlayerManager().sendToAll(subtitle);
	}
}
