package com.rufana.instantlore.client.resource;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ContentCache;
import com.rufana.instantlore.content.ContentManifest;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class LoreAssets {
	private static final Map<Identifier, String> BY_ID = new ConcurrentHashMap<>();

	private LoreAssets() {
	}

	private static volatile Map<String, String> byPath = Map.of();

	public static void rebuild(ContentManifest manifest) {
		BY_ID.clear();

		Map<String, String> paths = new java.util.LinkedHashMap<>();

		for (ContentManifest.AssetEntry asset : manifest.assets()) {
			if (asset.path().startsWith("assets/")) {
				Identifier id = toIdentifier(asset.path());

				if (id == null) {
					InstantLore.LOGGER.warn("Path '{}' starts with assets/ but does not look like assets/<namespace>/... - skipped",
							asset.path());
					continue;
				}

				BY_ID.put(id, asset.sha256());
			} else {
				paths.put(asset.path(), asset.sha256());
			}
		}

		byPath = java.util.Collections.unmodifiableMap(paths);
	}

	public static String sha256ForPath(String path) {
		return byPath.get(path);
	}

	public static Map<String, String> under(String prefix) {
		Map<String, String> result = new java.util.LinkedHashMap<>();
		byPath.forEach((path, sha256) -> {
			if (path.startsWith(prefix)) {
				result.put(path, sha256);
			}
		});
		return result;
	}

	/** {@code assets/instantlore/textures/block/altar.png} → {@code instantlore:textures/block/altar.png} */
	private static Identifier toIdentifier(String assetPath) {
		String[] parts = assetPath.split("/", 3);

		if (parts.length < 3 || !"assets".equals(parts[0])) {
			return null;
		}

		return Identifier.tryParse(parts[1] + ":" + parts[2]);
	}

	public static String sha256For(Identifier id) {
		return BY_ID.get(id);
	}

	public static byte[] read(String sha256) {
		try {
			return ContentCache.readAsset(sha256);
		} catch (IOException e) {
			throw new UncheckedIOException("Ассет " + sha256 + " пропал из кэша", e);
		}
	}

	public static void forEachTexture(BiConsumer<Identifier, String> consumer) {
		BY_ID.forEach(consumer);
	}
}
