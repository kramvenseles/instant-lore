package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CanonicalPack {

	private static final Path OVERRIDE = FabricLoader.getInstance().getConfigDir()
			.resolve(InstantLore.MOD_ID).resolve("pack.tsv");

	private static final String RESOURCE = "/canonical-pack.tsv";

	public record CanonMod(String version, boolean optional) {
	}

	private static volatile Map<String, CanonMod> mods;

	private static volatile String label = "?";

	private CanonicalPack() {
	}

	public static Map<String, CanonMod> mods() {
		Map<String, CanonMod> m = mods;
		if (m == null) {
			synchronized (CanonicalPack.class) {
				m = mods;
				if (m == null) {
					m = load();
					mods = m;
				}
			}
		}
		return m;
	}

	public static String label() {
		mods();
		return label;
	}

	public static boolean contains(String modid) {
		return mods().containsKey(modid);
	}

	public static CanonMod get(String modid) {
		return mods().get(modid);
	}

	public static synchronized void reload() {
		mods = null;
		label = "?";
	}

	private static Map<String, CanonMod> load() {
		try {
			if (Files.isRegularFile(OVERRIDE)) {
				try (BufferedReader r = Files.newBufferedReader(OVERRIDE, StandardCharsets.UTF_8)) {
					Map<String, CanonMod> parsed = parse(r);
					label = labelFor("config", parsed.size());
					InstantLore.LOGGER.info("[limbo-audit] canon from override {} ({} mods)",
							OVERRIDE, parsed.size());
					return parsed;
				}
			}
			try (InputStream in = CanonicalPack.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					InstantLore.LOGGER.warn("[limbo-audit] no bundled canon - diff has no reference");
					label = "none";
					return Map.of();
				}
				try (BufferedReader r = new BufferedReader(
						new InputStreamReader(in, StandardCharsets.UTF_8))) {
					Map<String, CanonMod> parsed = parse(r);
					label = labelFor("resource", parsed.size());
					return parsed;
				}
			}
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("[limbo-audit] canon not read - diff has no reference", e);
			label = "error";
			return Map.of();
		}
	}

	private static Map<String, CanonMod> parse(BufferedReader r) throws java.io.IOException {
		Map<String, CanonMod> out = new LinkedHashMap<>();
		String line;
		boolean labelTaken = false;
		while ((line = r.readLine()) != null) {
			String trimmed = line.strip();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("#")) {
				if (!labelTaken) {
					snapshotLabel = trimmed.replaceFirst("^#\\s*", "");
					labelTaken = true;
				}
				continue;
			}
			// file<TAB>modid<TAB>version<TAB>sha512<TAB>url
			String[] parts = line.split("\t", -1);
			if (parts.length < 3) {
				continue;
			}
			boolean optional = parts[0].strip().toLowerCase(Locale.ROOT).startsWith("[optional]");
			String modid = parts[1].strip().toLowerCase(Locale.ROOT);
			String version = parts[2].strip();
			if (!modid.isEmpty() && !modid.equals("?")) {
				out.put(modid, new CanonMod(version, optional));
			}
		}
		return out;
	}

	private static volatile String snapshotLabel = "";

	private static String labelFor(String source, int count) {
		String snap = snapshotLabel;
		return (snap == null || snap.isEmpty() ? source : snap) + " · " + count + " модов";
	}
}
