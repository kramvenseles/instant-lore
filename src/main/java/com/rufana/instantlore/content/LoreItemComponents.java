package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LoreItemComponents implements ComponentMap {
	private final int slot;
	private final ComponentMap base;

	private LoreItemDefinition cachedFor;
	private ComponentMap cached;

	public LoreItemComponents(int slot, ComponentMap base) {
		this.slot = slot;
		this.base = base;
		this.cachedFor = null;
		this.cached = base;
	}

	private synchronized ComponentMap current() {
		LoreItemDefinition definition = LoreContent.item(slot);

		if (definition == cachedFor) {
			return cached;
		}

		ComponentMap built;

		try {
			built = ComponentMap.of(base, build(definition));
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Item slot {} components failed to build, using defaults", slot, e);
			built = base;
		}

		cachedFor = definition;
		cached = built;
		return built;
	}

	private static ComponentMap build(LoreItemDefinition definition) {
		ComponentMap.Builder builder = ComponentMap.builder();

		int stack = Math.max(1, Math.min(99, definition.stack()));
		builder.add(DataComponentTypes.MAX_STACK_SIZE, stack);

		if (definition.durability() > 0 && stack == 1) {
			builder.add(DataComponentTypes.MAX_DAMAGE, definition.durability());
			builder.add(DataComponentTypes.DAMAGE, 0);
		}

		builder.add(DataComponentTypes.RARITY, rarity(definition.rarity()));

		if (definition.glint()) {
			builder.add(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		}

		List<String> lore = definition.lore();

		if (lore != null && !lore.isEmpty()) {
			List<Text> lines = new ArrayList<>(lore.size());

			for (String line : lore.subList(0, Math.min(lore.size(), LoreComponent.MAX_LORES))) {
				lines.add(Text.literal(line));
			}

			builder.add(DataComponentTypes.LORE, new LoreComponent(lines));
		}

		String bannerTag = definition.bannerPatternTag();
		if (bannerTag != null && !bannerTag.isBlank()) {
			Identifier tagId = Identifier.tryParse(bannerTag);
			if (tagId != null) {
				builder.add(DataComponentTypes.PROVIDES_BANNER_PATTERNS,
						TagKey.of(RegistryKeys.BANNER_PATTERN, tagId));
			} else {
				InstantLore.LOGGER.warn("bannerPatternTag '{}' does not parse as Identifier - item will not become a pattern",
						bannerTag);
			}
		}

		if (definition.wearable()) {
			net.minecraft.entity.EquipmentSlot slot = equipSlot(definition.equip());
			if (slot != null) {
				builder.add(DataComponentTypes.EQUIPPABLE,
						net.minecraft.component.type.EquippableComponent.builder(slot)
								.model(equipAsset(definition.equipAsset()))
								.swappable(true)
								.build());
			} else {
				InstantLore.LOGGER.warn("equip '{}' - unknown slot, item will not become wearable",
						definition.equip());
			}
		}

		return builder.build();
	}

	private static net.minecraft.entity.EquipmentSlot equipSlot(String raw) {
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "head", "helmet" -> net.minecraft.entity.EquipmentSlot.HEAD;
			case "chest", "chestplate" -> net.minecraft.entity.EquipmentSlot.CHEST;
			case "legs", "leggings" -> net.minecraft.entity.EquipmentSlot.LEGS;
			case "feet", "boots" -> net.minecraft.entity.EquipmentSlot.FEET;
			default -> null;
		};
	}

	private static net.minecraft.registry.RegistryKey<net.minecraft.item.equipment.EquipmentAsset>
			equipAsset(String raw) {
		return switch (raw == null ? "" : raw.toLowerCase(Locale.ROOT)) {
			case "chainmail" -> net.minecraft.item.equipment.EquipmentAssetKeys.CHAINMAIL;
			case "copper" -> net.minecraft.item.equipment.EquipmentAssetKeys.COPPER;
			case "iron" -> net.minecraft.item.equipment.EquipmentAssetKeys.IRON;
			case "gold", "golden" -> net.minecraft.item.equipment.EquipmentAssetKeys.GOLD;
			case "diamond" -> net.minecraft.item.equipment.EquipmentAssetKeys.DIAMOND;
			case "netherite" -> net.minecraft.item.equipment.EquipmentAssetKeys.NETHERITE;
			case "turtle_scute", "turtle" -> net.minecraft.item.equipment.EquipmentAssetKeys.TURTLE_SCUTE;
			case "armadillo_scute", "armadillo" -> net.minecraft.item.equipment.EquipmentAssetKeys.ARMADILLO_SCUTE;
			default -> net.minecraft.item.equipment.EquipmentAssetKeys.LEATHER;
		};
	}

	private static Rarity rarity(String raw) {
		if (raw == null) {
			return Rarity.COMMON;
		}

		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "uncommon" -> Rarity.UNCOMMON;
			case "rare" -> Rarity.RARE;
			case "epic" -> Rarity.EPIC;
			default -> Rarity.COMMON;
		};
	}

	@Override
	public <T> T get(ComponentType<? extends T> type) {
		return current().get(type);
	}

	@Override
	public Set<ComponentType<?>> getTypes() {
		return current().getTypes();
	}
}
