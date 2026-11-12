package com.rufana.instantlore.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public final class TintHandler {
	private TintHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("tint.set", event -> {
			Set<Long> positions = new HashSet<>();

			if (event.data().has("blocks")) {
				try {
					JsonArray blocks = event.data().getAsJsonArray("blocks");

					for (JsonElement element : blocks) {
						JsonArray xyz = element.getAsJsonArray();
						positions.add(BlockPos.asLong(
								xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
					}
				} catch (RuntimeException e) {
					InstantLore.LOGGER.warn("tint.set: failed to parse 'blocks'", e);
				}
			}

			ClientTintedBlocks.setAll(positions, resolveColor(event));
		});

		LoreEventBus.subscribe("tint.clear", event -> ClientTintedBlocks.clear());
	}

	private static int resolveColor(com.rufana.instantlore.event.LoreEvent event) {
		float factor = (float) event.number("factor", 0.35);
		float density = 1.0F - Math.max(0.0F, Math.min(1.0F, factor));
		int alpha = Math.min(210, (int) (density * 240.0F));

		String spec = event.string("color", null);

		if (spec == null) {
			return (alpha << 24) | 0x8F8A82;
		}

		String raw = spec.replace("#", "");

		try {
			long parsed = Long.parseLong(raw, 16);
			return raw.length() > 6 ? (int) parsed : (alpha << 24) | ((int) parsed & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return (alpha << 24) | 0x8F8A82;
		}
	}
}
