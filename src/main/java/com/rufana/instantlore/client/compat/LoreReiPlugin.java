package com.rufana.instantlore.client.compat;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreContent;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;

public final class LoreReiPlugin implements REIClientPlugin {

	@Override
	public void registerEntries(EntryRegistry registry) {
		Predicate<EntryStack<?>> undeclared = LoreReiPlugin::isUndeclaredPoolItem;
		registry.removeEntryIf(undeclared);
	}

	private static boolean isUndeclaredPoolItem(EntryStack<?> entry) {
		try {
			if (!(entry.getValue() instanceof ItemStack stack)) {
				return false;
			}

			Identifier id = Registries.ITEM.getId(stack.getItem());
			if (!InstantLore.MOD_ID.equals(id.getNamespace())) {
				return false;
			}

			String path = id.getPath();
			if (path.startsWith("item_")) {
				int slot = slotOf(path, "item_");
				return slot >= 0 && !LoreContent.isItemDeclared(slot);
			}
			if (path.startsWith("block_")) {
				int slot = slotOf(path, "block_");
				return slot >= 0 && !LoreContent.isBlockDeclared(slot);
			}
			return false;
		} catch (Throwable t) {
			return false;
		}
	}

	private static int slotOf(String path, String prefix) {
		try {
			return Integer.parseInt(path.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
