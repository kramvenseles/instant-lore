package com.rufana.instantlore.content;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record ContentManifest(String version, List<BlockEntry> blocks, List<ItemEntry> items,
		List<EntityEntry> entities, List<AssetEntry> assets) {
	private static final Gson GSON = new Gson();

	public record BlockEntry(
			int slot, String name, String title, float hardness, float resistance,
			int light, String sound, boolean solid,
			Boolean container, String label, Double tick,
			String drop, String tool,
			List<Double> shape, Boolean outline,
			Boolean firework
	) {
	}

	public record ItemEntry(
			int slot, String name, String title, List<String> lore,
			Integer stack, Integer durability, String rarity, Boolean glint, Boolean usable,
			String useAction, Integer useTicks,
			String bannerPatternTag,
			Integer coin, Boolean purse,
			String equip, String equipAsset
	) {
	}

	public record EntityEntry(
			int slot, String name, String title, Integer block, Double size, Double scale,
			Double health, Double speed, Boolean wander, Boolean look, Boolean gravity,
			Boolean invulnerable, List<Double> hitbox, Boolean clickable, Boolean collidable
	) {
	}

	public record AssetEntry(String path, String sha256, long size) {
	}

	public static ContentManifest parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();

		String version = root.has("version") ? root.get("version").getAsString() : "0";

		List<BlockEntry> blocks = new ArrayList<>();
		if (root.has("blocks")) {
			root.getAsJsonArray("blocks").forEach(element ->
					blocks.add(GSON.fromJson(element, BlockEntry.class)));
		}

		List<ItemEntry> items = new ArrayList<>();
		if (root.has("items")) {
			root.getAsJsonArray("items").forEach(element ->
					items.add(GSON.fromJson(element, ItemEntry.class)));
		}

		List<EntityEntry> entities = new ArrayList<>();
		if (root.has("entities")) {
			root.getAsJsonArray("entities").forEach(element ->
					entities.add(GSON.fromJson(element, EntityEntry.class)));
		}

		List<AssetEntry> assets = new ArrayList<>();
		if (root.has("assets")) {
			root.getAsJsonArray("assets").forEach(element ->
					assets.add(GSON.fromJson(element, AssetEntry.class)));
		}

		return new ContentManifest(version, blocks, items, entities, assets);
	}
}
