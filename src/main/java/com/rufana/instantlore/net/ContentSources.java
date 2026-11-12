package com.rufana.instantlore.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ContentSources {
	private static final List<String> SEEDS = List.of(
			"https://kramvenseles.github.io/instantlore-content/"
	);

	private static final int MAX_SOURCES = 8;

	private static final int MAX_DOC_BYTES = 8 * 1024;

	private static final Path ROOT =
			FabricLoader.getInstance().getGameDir().resolve("instantlore");

	private static final Path DOC = ROOT.resolve("sources.json");
	private static final Path SIG = ROOT.resolve("sources.sig");

	private static volatile List<String> sources = SEEDS;
	private static volatile long serial = 0L;

	private static volatile byte[] docBytes;
	private static volatile byte[] sigBytes;

	private static volatile String preferred;

	private ContentSources() {
	}

	public static List<String> ordered() {
		Set<String> ordered = new LinkedHashSet<>();

		String sticky = preferred;

		if (sticky != null) {
			ordered.add(sticky);
		}

		ordered.addAll(sources);
		ordered.addAll(SEEDS);
		return List.copyOf(ordered);
	}

	public static void succeeded(String base) {
		if (!base.equals(preferred)) {
			InstantLore.LOGGER.debug("Content is fetched from {}", base);
		}

		preferred = base;
	}

	public static void startCycle() {
		preferred = null;
	}

	public static void failed(String base) {
		if (base.equals(preferred)) {
			preferred = null;
		}
	}

	public static long serial() {
		return serial;
	}

	public static List<String> current() {
		return sources;
	}

	public static byte[][] document() {
		byte[] doc = docBytes;
		byte[] sig = sigBytes;
		return doc == null || sig == null ? null : new byte[][] {doc, sig};
	}

	public static boolean loadFromDisk() {
		try {
			if (!Files.exists(DOC) || !Files.exists(SIG)) {
				return false;
			}

			return accept(Files.readAllBytes(DOC), Files.readAllBytes(SIG), "с диска", false);
		} catch (Exception e) {
			InstantLore.LOGGER.warn("Local source list unreadable, using the seed list", e);
			return false;
		}
	}

	public static synchronized boolean accept(byte[] doc, byte[] sig, String origin,
			boolean persist) {
		if (doc == null || sig == null || doc.length == 0 || doc.length > MAX_DOC_BYTES) {
			InstantLore.LOGGER.warn("Source list {}: size not acceptable", origin);
			return false;
		}

		int key = ContentSignature.verifyBy(doc, sig);

		if (key == ContentSignature.NONE) {
			InstantLore.LOGGER.warn("Source list {}: signature mismatch - rejected", origin);
			return false;
		}

		JsonObject root;

		try {
			root = JsonParser.parseString(new String(doc, StandardCharsets.UTF_8))
					.getAsJsonObject();
		} catch (Exception e) {
			InstantLore.LOGGER.warn("Source list {}: does not parse as JSON", origin, e);
			return false;
		}

		long incoming = root.has("serial") ? root.get("serial").getAsLong() : -1L;

		if (incoming <= serial) {
			InstantLore.LOGGER.warn("Source list {}: serial {} is not newer than accepted {} - rejected",
					origin, incoming, serial);
			return false;
		}

		List<String> parsed = parseSources(root, origin);

		if (parsed.isEmpty()) {
			InstantLore.LOGGER.warn("Source list {}: no acceptable address - rejected", origin);
			return false;
		}

		if (key == ContentSignature.BACKUP) {
			applyRevocation(root, origin);
		} else if (root.has("revoke")) {
			InstantLore.LOGGER.warn("Source list {}: revoke field signed with the working key "
					+ "and therefore ignored - only the backup key can revoke", origin);
		}

		sources = parsed;
		serial = incoming;
		docBytes = doc.clone();
		sigBytes = sig.clone();
		preferred = null;

		InstantLore.LOGGER.debug("Source list accepted {} (serial {}, key {}): {}",
				origin, incoming, key == ContentSignature.BACKUP ? "backup" : "working", parsed);

		if (persist) {
			persist(doc, sig);
		}

		return true;
	}

	private static List<String> parseSources(JsonObject root, String origin) {
		List<String> parsed = new ArrayList<>();

		if (!root.has("sources") || !root.get("sources").isJsonArray()) {
			return parsed;
		}

		for (JsonElement element : root.getAsJsonArray("sources")) {
			if (parsed.size() >= MAX_SOURCES) {
				InstantLore.LOGGER.warn("Source list {}: longer than {} - tail dropped",
						origin, MAX_SOURCES);
				break;
			}

			String base = element.isJsonPrimitive() ? element.getAsString() : null;

			if (base == null || !valid(base)) {
				InstantLore.LOGGER.warn("Source list {}: address '{}' is not acceptable - skipped",
						origin, base);
				continue;
			}

			parsed.add(base);
		}

		return parsed;
	}

	private static void applyRevocation(JsonObject root, String origin) {
		if (!root.has("revoke") || !root.get("revoke").isJsonArray()) {
			return;
		}

		List<Integer> keys = new ArrayList<>();

		for (JsonElement element : root.getAsJsonArray("revoke")) {
			try {
				keys.add(element.getAsInt());
			} catch (Exception ignored) {
			}
		}

		if (ContentSignature.revoke(keys)) {
			InstantLore.LOGGER.error("Working signing key REVOKED (document {}). "
					+ "From now on only the backup key is accepted.", origin);
		}
	}

	private static boolean valid(String base) {
		if (!base.endsWith("/") || base.contains("..") || base.contains("@")) {
			return false;
		}

		try {
			URI uri = URI.create(base);
			String scheme = uri.getScheme();

			if (!"https".equals(scheme) && !"http".equals(scheme)) {
				return false;
			}

			return uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null
					&& base.equals(uri.normalize().toString());
		} catch (Exception e) {
			return false;
		}
	}

	private static void persist(byte[] doc, byte[] sig) {
		try {
			Files.createDirectories(ROOT);
			Files.write(SIG, sig);
			Files.write(DOC, doc);
		} catch (IOException e) {
			InstantLore.LOGGER.warn("Source list not saved to disk", e);
		}
	}
}
