package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.minecraft.sound.BlockSoundGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ContentApplier {
	private static final Map<String, BlockSoundGroup> SOUNDS = collectSoundGroups();

	public static int soundGroupCount() {
		return SOUNDS.size();
	}

	private ContentApplier() {
	}

	public static int refreshDimensions(Iterable<? extends net.minecraft.entity.Entity> entities) {
		if (entities == null) {
			return 0;
		}

		int touched = 0;

		for (net.minecraft.entity.Entity entity : entities) {
			if (entity instanceof LoreShaped) {
				entity.calculateDimensions();
				touched++;
			}
		}

		return touched;
	}

	public static void apply(ContentManifest manifest) {
		LoreContent.reset();

		for (ContentManifest.BlockEntry entry : manifest.blocks()) {
			if (entry.slot() < 0 || entry.slot() >= LoreContent.POOL_SIZE) {
				InstantLore.LOGGER.warn("Slot {} outside the pool ({}), entry '{}' skipped",
						entry.slot(), LoreContent.POOL_SIZE, entry.name());
				continue;
			}

			LoreContent.defineBlock(entry.slot(), new LoreBlockDefinition(
					entry.name(),
					entry.title(),
					entry.hardness(),
					entry.resistance(),
					Math.clamp(entry.light(), 0, 15),
					sound(entry.sound()),
					entry.solid(),
					entry.container() != null && entry.container(),
					entry.label() == null ? "" : entry.label(),
					entry.tick() == null ? 0.0 : Math.max(0.0, entry.tick()),
					entry.drop() == null || entry.drop().isBlank() ? "self" : entry.drop().trim(),
					entry.tool() == null ? "" : entry.tool().trim(),
					shapeOf(entry),
					entry.outline() == null || entry.outline(),
					entry.firework() != null && entry.firework()));
		}

		for (ContentManifest.ItemEntry entry : manifest.items()) {
			if (entry.slot() < 0 || entry.slot() >= LoreContent.ITEM_POOL_SIZE) {
				InstantLore.LOGGER.warn("Item slot {} outside the pool ({}), entry '{}' skipped",
						entry.slot(), LoreContent.ITEM_POOL_SIZE, entry.name());
				continue;
			}

			LoreContent.defineItem(entry.slot(), new LoreItemDefinition(
					entry.name(),
					entry.title(),
					entry.lore() == null ? List.of() : List.copyOf(entry.lore()),
					entry.stack() == null ? 64 : Math.clamp(entry.stack(), 1, 99),
					entry.durability() == null ? 0 : Math.max(0, entry.durability()),
					entry.rarity(),
					entry.glint() != null && entry.glint(),
					entry.usable() != null && entry.usable(),
					entry.useAction() == null ? "none" : entry.useAction(),
					entry.useTicks() == null ? 32 : Math.clamp(entry.useTicks(), 1, 200),
					entry.bannerPatternTag() == null ? "" : entry.bannerPatternTag().trim(),
					entry.coin() == null ? 0 : Math.max(0, entry.coin()),
					entry.purse() != null && entry.purse(),
					entry.equip() == null ? "" : entry.equip().trim(),
					entry.equipAsset() == null || entry.equipAsset().isBlank()
							? "leather" : entry.equipAsset().trim()));
		}

		for (ContentManifest.EntityEntry entry : manifest.entities()) {
			if (entry.slot() < 0 || entry.slot() >= LoreContent.ENTITY_POOL_SIZE) {
				InstantLore.LOGGER.warn("Entity slot {} outside the pool ({}), entry '{}' skipped",
						entry.slot(), LoreContent.ENTITY_POOL_SIZE, entry.name());
				continue;
			}

			double size = entry.size() == null ? 1.0 : Math.clamp(entry.size(), 0.1, 16.0);
			double[] hitbox = hitboxOf(entry, size);

			LoreContent.defineEntity(entry.slot(), new LoreEntityDefinition(
					entry.name(),
					entry.title() == null ? "" : entry.title(),
					entry.block() == null ? 0 : Math.clamp(entry.block(), 0, LoreContent.POOL_SIZE - 1),
					size,
					hitbox[0],
					hitbox[1],
					entry.scale() == null ? 1.0 : Math.clamp(entry.scale(), 0.05, 16.0),
					entry.health() == null ? 20.0 : Math.max(1.0, entry.health()),
					entry.speed() == null ? 0.25 : Math.max(0.0, entry.speed()),
					entry.wander() != null && entry.wander(),
					entry.look() != null && entry.look(),
					entry.gravity() == null || entry.gravity(),
					entry.invulnerable() != null && entry.invulnerable(),
					entry.clickable() == null || entry.clickable(),
					entry.collidable() != null && entry.collidable()));
		}

		InstantLore.LOGGER.debug(
				"Applied manifest version '{}': blocks {}, items {}, entities {}, assets {}",
				manifest.version(), manifest.blocks().size(), manifest.items().size(),
				manifest.entities().size(), manifest.assets().size());
	}

	private static double[] hitboxOf(ContentManifest.EntityEntry entry, double size) {
		double[] cube = {size, size};
		List<Double> box = entry.hitbox();

		if (box == null) {
			return cube;
		}

		if (box.size() != 2) {
			InstantLore.LOGGER.warn(
					"Entity '{}': hitbox has {} numbers instead of two - using cube {}x{}",
					entry.name(), box.size(), size, size);
			return cube;
		}

		if (box.get(0) == null || box.get(1) == null) {
			InstantLore.LOGGER.warn("Entity '{}': a number is missing in the hitbox - using cube {}x{}",
					entry.name(), size, size);
			return cube;
		}

		return new double[]{
				Math.clamp(box.get(0), 0.05, 24.0),
				Math.clamp(box.get(1), 0.05, 24.0)};
	}

	private static double[] shapeOf(ContentManifest.BlockEntry entry) {
		List<Double> box = entry.shape();

		if (box == null) {
			return null;
		}

		if (box.size() != 6) {
			InstantLore.LOGGER.warn("Block '{}': dimensions have {} numbers instead of six - using a full cube",
					entry.name(), box.size());
			return null;
		}

		double[] out = new double[6];
		for (int i = 0; i < 6; i++) {
			Double value = box.get(i);

			if (value == null) {
				InstantLore.LOGGER.warn("Block '{}': a number is missing in the dimensions - using a full cube",
						entry.name());
				return null;
			}

			out[i] = Math.clamp(value, 0.0, 16.0);
		}

		return out;
	}

	private static BlockSoundGroup sound(String name) {
		if (name == null) {
			return BlockSoundGroup.STONE;
		}

		BlockSoundGroup group = SOUNDS.get(name.toUpperCase(Locale.ROOT));

		if (group == null) {
			InstantLore.LOGGER.warn("Unknown sound group '{}', using STONE", name);
			return BlockSoundGroup.STONE;
		}

		return group;
	}

	private static Map<String, BlockSoundGroup> collectSoundGroups() {
		Map<String, BlockSoundGroup> map = new HashMap<>();

		Field[] fields = BlockSoundGroup.class.getFields();
		Arrays.sort(fields, Comparator.comparing(Field::getName));

		for (Field field : fields) {
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != BlockSoundGroup.class) {
				continue;
			}

			try {
				BlockSoundGroup group = (BlockSoundGroup) field.get(null);

				if (group == null) {
					continue;
				}

				map.putIfAbsent(field.getName().toUpperCase(Locale.ROOT), group);

				String derived = keyFromBreakSound(group);

				if (derived != null) {
					map.putIfAbsent(derived, group);
				}
			} catch (Exception e) {
				InstantLore.LOGGER.warn("Failed to read sound group {}", field.getName());
			}
		}

		checkCanary(map);
		return map;
	}

	private static String keyFromBreakSound(BlockSoundGroup group) {
		try {
			String path = group.getBreakSound().id().getPath();

			if (path.startsWith("block.")) {
				path = path.substring("block.".length());
			}
			if (path.endsWith(".break")) {
				path = path.substring(0, path.length() - ".break".length());
			}

			return path.isEmpty() || path.contains(".") ? null : path.toUpperCase(Locale.ROOT);
		} catch (Exception e) {
			return null;
		}
	}

	private static void checkCanary(Map<String, BlockSoundGroup> map) {
		if (map.containsKey("STONE") && map.containsKey("WOOL")) {
			InstantLore.LOGGER.debug("Sound groups recognized: {}", map.size());
			return;
		}

		InstantLore.LOGGER.error("Sound group recognition broken: not even STONE/WOOL "
				+ "among {} keys. All blocks will sound like stone. "
				+ "The sound name scheme or mappings have probably changed.", map.size());
	}
}
