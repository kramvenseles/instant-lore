package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LoreItemPool {
	private static final Item[] POOL = new Item[LoreContent.ITEM_POOL_SIZE];

	private LoreItemPool() {
	}

	public static void register() {
		for (int slot = 0; slot < LoreContent.ITEM_POOL_SIZE; slot++) {
			POOL[slot] = registerSlot(slot);
		}

		InstantLore.LOGGER.info("Item pool registered: {} slots", LoreContent.ITEM_POOL_SIZE);
	}

	private static Item registerSlot(int slot) {
		Identifier id = Identifier.of(InstantLore.MOD_ID, "item_" + slot);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

		Item.Settings settings = new Item.Settings()
				.registryKey(key)
				.useItemPrefixedTranslationKey();

		return Registry.register(Registries.ITEM, key, new LoreItem(slot, settings));
	}

	public static Item get(int slot) {
		return POOL[slot];
	}
}
