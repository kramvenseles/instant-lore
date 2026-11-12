package com.rufana.instantlore.server.module;

import com.rufana.instantlore.server.ClientVersions;
import com.rufana.instantlore.server.LoreItemBus;
import com.rufana.instantlore.server.RitualRunner;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public final class RitualDagger {

	public static final String DAGGER_ITEM = "ritual_dagger";

	public static final int MIN_MAJOR = 0;
	public static final int MIN_MINOR = 59;
	public static final int MIN_PATCH = 2;

	private RitualDagger() {
	}

	public static void register() {
		LoreItemBus.subscribe((player, slot, definition, hand, pos) -> {
			if (!DAGGER_ITEM.equals(definition.name())) {
				return;
			}
			onUse(player, hand);
		});
	}

	private static void onUse(ServerPlayerEntity player, Hand hand) {
		if (!ClientVersions.meetsMinimum(player.getUuid(), MIN_MAJOR, MIN_MINOR, MIN_PATCH)) {
			player.sendMessage(Text.literal("Клинок не отзывается — твой мод слишком стар. "
					+ "Обнови до " + MIN_MAJOR + "." + MIN_MINOR + "." + MIN_PATCH + " и попробуй снова.")
					.formatted(Formatting.RED), false);
			return;
		}

		if (!RitualRunner.start(player)) {
			return;
		}

		consumeOne(player, hand);

		player.sendMessage(Text.literal("Ты заносишь клинок над собой…")
				.formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
	}

	private static void consumeOne(ServerPlayerEntity player, Hand hand) {
		player.getStackInHand(hand).decrement(1);
	}
}
