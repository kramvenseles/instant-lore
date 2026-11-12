package com.rufana.instantlore.client.resource;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreBlockDefinition;
import com.rufana.instantlore.content.LoreContent;
import com.rufana.instantlore.content.LoreItemDefinition;

public final class LoreModels {
	private static final String BLOCKSTATE_PREFIX = "blockstates/block_";
	private static final String MODEL_PREFIX = "models/block/block_";
	private static final String ITEM_PREFIX = "items/block_";

	private static final String POOL_ITEM_PREFIX = "items/item_";
	private static final String POOL_ITEM_MODEL_PREFIX = "models/item/item_";

	private static final String JSON_SUFFIX = ".json";

	private static final String PLACEHOLDER_TEXTURE = "minecraft:block/stone";
	private static final String PLACEHOLDER_ITEM_TEXTURE = "minecraft:item/stick";

	private LoreModels() {
	}

	public static Integer slotFromBlockstatePath(String path) {
		return slotFrom(path, BLOCKSTATE_PREFIX, LoreContent.POOL_SIZE);
	}

	public static Integer slotFromModelPath(String path) {
		return slotFrom(path, MODEL_PREFIX, LoreContent.POOL_SIZE);
	}

	public static Integer slotFromItemPath(String path) {
		return slotFrom(path, ITEM_PREFIX, LoreContent.POOL_SIZE);
	}

	public static Integer slotFromPoolItemPath(String path) {
		return slotFrom(path, POOL_ITEM_PREFIX, LoreContent.ITEM_POOL_SIZE);
	}

	public static Integer slotFromPoolItemModelPath(String path) {
		return slotFrom(path, POOL_ITEM_MODEL_PREFIX, LoreContent.ITEM_POOL_SIZE);
	}

	public static String itemJson(int slot) {
		return "{\"model\":{\"type\":\"minecraft:model\","
				+ "\"model\":\"instantlore:block/block_" + slot + "\"}}";
	}

	private static Integer slotFrom(String path, String prefix, int limit) {
		if (!path.startsWith(prefix) || !path.endsWith(JSON_SUFFIX)) {
			return null;
		}

		try {
			int slot = Integer.parseInt(
					path.substring(prefix.length(), path.length() - JSON_SUFFIX.length()));
			return (slot >= 0 && slot < limit) ? slot : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String poolItemJson(int slot) {
		return "{\"model\":{\"type\":\"minecraft:model\","
				+ "\"model\":\"instantlore:item/item_" + slot + "\"}}";
	}

	public static String poolItemModelJson(int slot) {
		String custom = customItemModelPath(slot);

		if (custom != null) {
			return "{\"parent\":\"" + custom + "\"}";
		}

		return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""
				+ itemTexture(slot) + "\"}}";
	}

	private static String customItemModelPath(int slot) {
		LoreItemDefinition definition = LoreContent.item(slot);

		if (definition == LoreItemDefinition.EMPTY) {
			return null;
		}

		net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(
				InstantLore.MOD_ID + ":models/item/" + definition.name() + ".json");

		return id == null || LoreAssets.sha256For(id) == null
				? null
				: "instantlore:item/" + definition.name();
	}

	private static String itemTexture(int slot) {
		LoreItemDefinition definition = LoreContent.item(slot);

		if (definition == LoreItemDefinition.EMPTY) {
			return PLACEHOLDER_ITEM_TEXTURE;
		}

		return "instantlore:item/" + definition.name();
	}

	public static String blockstateJson(int slot) {
		return "{\"multipart\":[{\"apply\":{\"model\":\"instantlore:block/block_" + slot + "\"}}]}";
	}

	public static String modelJson(int slot) {
		String custom = customModelPath(slot);

		if (custom != null) {
			return "{\"parent\":\"" + custom + "\"}";
		}

		return "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\""
				+ texture(slot) + "\"}}";
	}

	public static String customModelPath(int slot) {
		LoreBlockDefinition definition = LoreContent.block(slot);

		if (definition == LoreBlockDefinition.EMPTY) {
			return null;
		}

		net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(
				InstantLore.MOD_ID + ":models/block/" + definition.name() + ".json");

		return id == null || LoreAssets.sha256For(id) == null
				? null
				: "instantlore:block/" + definition.name();
	}

	private static String texture(int slot) {
		LoreBlockDefinition definition = LoreContent.block(slot);

		if (definition == LoreBlockDefinition.EMPTY) {
			return PLACEHOLDER_TEXTURE;
		}

		return "instantlore:block/" + definition.name();
	}
}
