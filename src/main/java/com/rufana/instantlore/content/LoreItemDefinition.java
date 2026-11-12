package com.rufana.instantlore.content;

import java.util.List;

public record LoreItemDefinition(
		String name,
		String title,
		List<String> lore,
		int stack,
		int durability,
		String rarity,
		boolean glint,
		boolean usable,
		String useAction,
		int useTicks,
		String bannerPatternTag,
		int coin,
		boolean purse,
		String equip,
		String equipAsset
) {
	public boolean wearable() {
		return equip != null && !equip.isBlank();
	}
	public String displayName() {
		return title == null || title.isBlank() ? name : title;
	}

	public boolean consumable() {
		return useAction != null && !useAction.isBlank() && !"none".equalsIgnoreCase(useAction);
	}

	public boolean isCoin() {
		return coin > 0;
	}

	public static final LoreItemDefinition EMPTY =
			new LoreItemDefinition("empty", "empty", List.of(), 64, 0, "common", false, false,
					"none", 0, "", 0, false, "", "leather");
}
