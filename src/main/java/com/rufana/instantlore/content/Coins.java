package com.rufana.instantlore.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Coins {

	public record Denom(int slot, int value) {
	}

	private Coins() {
	}

	public static List<Denom> descending() {
		List<Denom> out = new ArrayList<>();
		for (int slot = 0; slot < LoreContent.ITEM_POOL_SIZE; slot++) {
			LoreItemDefinition def = LoreContent.item(slot);
			if (def.isCoin()) {
				out.add(new Denom(slot, def.coin()));
			}
		}
		out.sort(Comparator.comparingInt(Denom::value).reversed());
		return out;
	}

	public static Denom highestNotExceeding(long balance) {
		for (Denom d : descending()) {
			if (d.value() <= balance) {
				return d;
			}
		}
		return null;
	}
}
