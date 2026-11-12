package com.rufana.instantlore.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;

public class LoreBlockItem extends BlockItem {
	public LoreBlockItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public net.minecraft.text.Text getName(net.minecraft.item.ItemStack stack) {
		return getBlock() instanceof LoreBlock lore
				? net.minecraft.text.Text.literal(lore.definition().displayName())
				: super.getName(stack);
	}

	@Override
	protected BlockState getPlacementState(ItemPlacementContext context) {
		BlockState state = super.getPlacementState(context);

		if (state == null || !(getBlock() instanceof LoreBlock lore)) {
			return state;
		}

		return state.with(LoreBlock.LIGHT, LoreBlock.nearestStep(lore.definition().luminance()));
	}
}
