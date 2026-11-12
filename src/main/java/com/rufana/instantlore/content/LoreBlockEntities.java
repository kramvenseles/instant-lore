package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class LoreBlockEntities {
	public static BlockEntityType<LoreBlockEntity> TYPE;

	private LoreBlockEntities() {
	}

	public static void register() {
		List<Block> blocks = new ArrayList<>(LoreContent.POOL_SIZE);

		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			blocks.add(LoreBlocks.get(slot));
		}

		TYPE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
				Identifier.of(InstantLore.MOD_ID, "lore_block"),
				FabricBlockEntityTypeBuilder.create(LoreBlockEntity::new)
						.addBlocks(blocks)
						.build());

		InstantLore.LOGGER.info("Pool block entity registered for {} slots",
				LoreContent.POOL_SIZE);
	}
}
