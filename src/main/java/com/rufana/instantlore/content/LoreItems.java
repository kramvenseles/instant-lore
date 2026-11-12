package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LoreItems {
	private static final Item[] POOL = new Item[LoreContent.POOL_SIZE];

	private LoreItems() {
	}

	public static void register() {
		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			POOL[slot] = registerSlot(slot);
		}

		InstantLore.LOGGER.info("Pool items registered: {}", LoreContent.POOL_SIZE);
	}

	private static Item registerSlot(int slot) {
		Identifier id = Identifier.of(InstantLore.MOD_ID, "block_" + slot);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

		Item.Settings settings = new Item.Settings()
				.registryKey(key)
				.useBlockPrefixedTranslationKey();

		return Registry.register(Registries.ITEM, key,
				new LoreBlockItem(LoreBlocks.get(slot), settings));
	}

	public static Item get(int slot) {
		return POOL[slot];
	}
}
