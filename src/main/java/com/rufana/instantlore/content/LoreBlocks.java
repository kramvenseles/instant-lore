package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LoreBlocks {
	private static final LoreBlock[] POOL = new LoreBlock[LoreContent.POOL_SIZE];

	private LoreBlocks() {
	}

	public static void register() {
		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			POOL[slot] = registerSlot(slot);
		}

		InstantLore.LOGGER.info("Pool registered: {} slots", LoreContent.POOL_SIZE);
	}

	private static LoreBlock registerSlot(int slot) {
		Identifier id = Identifier.of(InstantLore.MOD_ID, "block_" + slot);
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);

		AbstractBlock.Settings settings = AbstractBlock.Settings.create()
				.registryKey(key)
				.luminance(state -> LoreBlock.lightLevel(state.get(LoreBlock.LIGHT)))
				.dynamicBounds()
				.nonOpaque();

		return Registry.register(Registries.BLOCK, key, new LoreBlock(slot, settings));
	}

	public static LoreBlock get(int slot) {
		return POOL[slot];
	}
}
