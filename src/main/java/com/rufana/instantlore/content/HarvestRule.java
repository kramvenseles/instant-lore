package com.rufana.instantlore.content;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;

public record HarvestRule(String kind, int tier) {
	public static final HarvestRule ANY = new HarvestRule("", 0);

	private static final Map<String, Integer> TIERS = Map.of(
			"wooden", 0,
			"golden", 0,
			"stone", 1,
			"iron", 2,
			"diamond", 3,
			"netherite", 4);

	private static final Map<String, Integer> TIER_NAMES = Map.of(
			"any", 0,
			"wood", 0,
			"gold", 0,
			"stone", 1,
			"iron", 2,
			"diamond", 3,
			"netherite", 4);

	public static HarvestRule parse(String spec) {
		if (spec == null || spec.isBlank()) {
			return ANY;
		}

		String[] parts = spec.toLowerCase(Locale.ROOT).trim().split(":", 2);
		String kind = parts[0].trim();

		if (kind.isEmpty() || kind.equals("any") || kind.equals("none")) {
			return ANY;
		}

		int tier = 0;
		if (parts.length == 2) {
			tier = TIER_NAMES.getOrDefault(parts[1].trim(), 0);
		}

		return new HarvestRule(kind, tier);
	}

	public boolean satisfiedBy(ItemStack stack) {
		if (this == ANY || kind.isEmpty()) {
			return true;
		}

		if (stack == null || stack.isEmpty()) {
			return false;
		}

		Identifier id = Registries.ITEM.getId(stack.getItem());

		if (!"minecraft".equals(id.getNamespace())) {
			return false;
		}

		String path = id.getPath();

		if (!path.endsWith("_" + kind) && !path.equals(kind)) {
			return false;
		}

		int index = path.lastIndexOf('_');
		int actual = index < 0 ? 0 : TIERS.getOrDefault(path.substring(0, index), -1);

		return actual >= tier;
	}
}
