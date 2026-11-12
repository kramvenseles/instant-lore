package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.ContentManifest;
import com.rufana.instantlore.net.ContentSignature;
import com.rufana.instantlore.net.ContentSource;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ContentCache {
	private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("instantlore");
	private static final Path ASSETS = ROOT.resolve("assets");
	private static final Path MANIFEST = ROOT.resolve("manifest.json");
	private static final Path SIGNATURE = ROOT.resolve("manifest.sig");

	private static final java.util.regex.Pattern SHA256 =
			java.util.regex.Pattern.compile("[0-9a-f]{64}");

	private ContentCache() {
	}

	public static ContentManifest loadCachedManifest() {
		try {
			if (!Files.exists(MANIFEST) || !Files.exists(SIGNATURE)) {
				return null;
			}

			byte[] manifest = Files.readAllBytes(MANIFEST);

			if (!ContentSignature.verify(manifest, Files.readAllBytes(SIGNATURE))) {
				InstantLore.LOGGER.error("Local manifest signature mismatch - "
						+ "cache ignored, content will be downloaded again");
				return null;
			}

			return ContentManifest.parse(new String(manifest, StandardCharsets.UTF_8));
		} catch (Exception e) {
			InstantLore.LOGGER.warn("Local manifest unreadable, treating it as absent", e);
			return null;
		}
	}

	public static boolean hasAsset(String sha256) {
		return Files.exists(assetPath(sha256));
	}

	public static byte[] readAsset(String sha256) throws IOException {
		return Files.readAllBytes(assetPath(sha256));
	}

	public static int sync(ContentManifest manifest, ContentSource.SignedManifest signed)
			throws IOException, InterruptedException {
		Files.createDirectories(ASSETS);

		int downloaded = 0;

		for (ContentManifest.AssetEntry asset : manifest.assets()) {
			if (hasAsset(asset.sha256())) {
				continue;
			}

			byte[] data = ContentSource.fetchAsset(asset.path(), asset.sha256(), asset.size());
			Path target = assetPath(asset.sha256());

			Path temp = Files.createTempFile(ASSETS, "dl", ".part");
			Files.write(temp, data);
			Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			downloaded++;
		}

		Files.write(SIGNATURE, signed.signature());
		Files.writeString(MANIFEST, signed.json(), StandardCharsets.UTF_8);
		return downloaded;
	}

	private static Path assetPath(String sha256) {
		if (sha256 == null || !SHA256.matcher(sha256).matches()) {
			throw new IllegalArgumentException("Не похоже на sha256: " + sha256);
		}
		return ASSETS.resolve(sha256);
	}
}
