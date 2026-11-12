package com.rufana.instantlore.content;

import java.util.Arrays;

public final class LoreContent {
	public static final int POOL_SIZE = 4096;

	public static final int ITEM_POOL_SIZE = 4096;

	public static final int ENTITY_POOL_SIZE = 4096;

	private static final LoreBlockDefinition[] BLOCKS = new LoreBlockDefinition[POOL_SIZE];
	private static final LoreItemDefinition[] ITEMS = new LoreItemDefinition[ITEM_POOL_SIZE];
	private static final LoreEntityDefinition[] ENTITIES = new LoreEntityDefinition[ENTITY_POOL_SIZE];

	static {
		Arrays.fill(BLOCKS, LoreBlockDefinition.EMPTY);
		Arrays.fill(ITEMS, LoreItemDefinition.EMPTY);
		Arrays.fill(ENTITIES, LoreEntityDefinition.EMPTY);
	}

	private LoreContent() {
	}

	public static LoreBlockDefinition block(int slot) {
		if (slot < 0 || slot >= POOL_SIZE) {
			return LoreBlockDefinition.EMPTY;
		}
		return BLOCKS[slot];
	}

	public static void defineBlock(int slot, LoreBlockDefinition definition) {
		if (slot < 0 || slot >= POOL_SIZE) {
			throw new IllegalArgumentException("Слот " + slot + " вне пула размером " + POOL_SIZE);
		}
		BLOCKS[slot] = definition;
	}

	public static LoreItemDefinition item(int slot) {
		if (slot < 0 || slot >= ITEM_POOL_SIZE) {
			return LoreItemDefinition.EMPTY;
		}
		return ITEMS[slot];
	}

	public static void defineItem(int slot, LoreItemDefinition definition) {
		if (slot < 0 || slot >= ITEM_POOL_SIZE) {
			throw new IllegalArgumentException(
					"Слот предмета " + slot + " вне пула размером " + ITEM_POOL_SIZE);
		}
		ITEMS[slot] = definition;
	}

	public static boolean isItemDeclared(int slot) {
		return item(slot) != LoreItemDefinition.EMPTY;
	}

	public static boolean isBlockDeclared(int slot) {
		return block(slot) != LoreBlockDefinition.EMPTY;
	}

	public static LoreEntityDefinition entity(int slot) {
		if (slot < 0 || slot >= ENTITY_POOL_SIZE) {
			return LoreEntityDefinition.EMPTY;
		}
		return ENTITIES[slot];
	}

	public static void defineEntity(int slot, LoreEntityDefinition definition) {
		if (slot < 0 || slot >= ENTITY_POOL_SIZE) {
			throw new IllegalArgumentException(
					"Слот сущности " + slot + " вне пула размером " + ENTITY_POOL_SIZE);
		}
		ENTITIES[slot] = definition;
	}

	public static void reset() {
		Arrays.fill(BLOCKS, LoreBlockDefinition.EMPTY);
		Arrays.fill(ITEMS, LoreItemDefinition.EMPTY);
		Arrays.fill(ENTITIES, LoreEntityDefinition.EMPTY);
	}
}
