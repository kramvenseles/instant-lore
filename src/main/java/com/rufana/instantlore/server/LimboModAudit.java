package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.module.InvisibleModule;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimboModAudit {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve(InstantLore.MOD_ID).resolve("limbo-mods.jsonl");

	private static final DateTimeFormatter TS =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	private static final Object LOCK = new Object();
	private static BufferedWriter writer;

	private static final ConcurrentHashMap<UUID, String> LAST_DIFF = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<UUID, String> FULL_SIG = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<UUID, Set<String>> TG_ALERTED = new ConcurrentHashMap<>();

	private LimboModAudit() {
	}

	public static void record(ServerPlayerEntity player) {
		try {
			if (player == null) {
				return;
			}
			UUID uuid = player.getUuid();
			if (InvisibleModule.isHidden(uuid)) {
				return;
			}

			Doctor.Passport passport = Doctor.passport(uuid);
			List<Doctor.ModEntry> mods;
			String source;
			boolean truncated;
			if (passport != null) {
				source = "passport";
				mods = passport.mods();
				truncated = passport.truncated();
			} else {
				ClientMods.Report report = ClientMods.reported(uuid);
				if (report != null) {
					source = "legacy";
					mods = legacyMods(report);
					truncated = report.truncated();
				} else {
					source = "none";
					mods = List.of();
					truncated = false;
				}
			}

			if (!mods.isEmpty()) {
				String sig = composition(mods);
				if (!sig.equals(FULL_SIG.get(uuid))) {
					FULL_SIG.put(uuid, sig);
					writeLine(fullJson(player, passport, source, truncated, mods));
				}
			}

			Diff diff = diff(player, mods, passport != null);
			String fp = diff.fingerprint(source);
			if (!fp.equals(LAST_DIFF.get(uuid))) {
				LAST_DIFF.put(uuid, fp);
				writeLine(diffJson(player, source, diff));
			}

			alert(player, diff);
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("[limbo-audit] write failed", e);
		}
	}

	public static void forget(UUID uuid) {
		if (uuid != null) {
			LAST_DIFF.remove(uuid);
			FULL_SIG.remove(uuid);
			TG_ALERTED.remove(uuid);
		}
	}

	private static void alert(ServerPlayerEntity player, Diff diff) {
		List<Extra> interesting = new ArrayList<>();
		for (Extra e : diff.extra()) {
			if (e.cheat() || !e.risk().isEmpty()) {
				interesting.add(e);
			}
		}
		if (interesting.isEmpty() && diff.hidden().isEmpty()) {
			return;
		}

		UUID uuid = player.getUuid();
		Set<String> alerted = TG_ALERTED.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
		TreeSet<String> sigParts = new TreeSet<>();
		for (Extra e : interesting) {
			sigParts.add(e.id());
		}
		for (String h : diff.hidden()) {
			sigParts.add("h:" + h);
		}
		if (!alerted.add(String.join(";", sigParts))) {
			return;
		}

		boolean cheat = interesting.stream().anyMatch(Extra::cheat);
		List<String> ids = new ArrayList<>();
		for (Extra e : interesting) {
			ids.add(e.id());
		}
		String text = (cheat ? "Чит-клиент в лимбо" : "Coord-инструменты в лимбо")
				+ (ids.isEmpty() ? "" : ": " + String.join(", ", ids));

		StringBuilder details = new StringBuilder();
		for (Extra e : interesting) {
			if (details.length() > 0) {
				details.append('\n');
			}
			details.append(e.cheat() ? "⛔ чит — " : "⚠️ " + e.risk() + " — ").append(e.id());
			if (!e.version().isEmpty()) {
				details.append(' ').append(e.version());
			}
		}
		for (String h : diff.hidden()) {
			if (details.length() > 0) {
				details.append('\n');
			}
			details.append("🔎 улика (канал): ").append(h);
		}

		AlertSink.send(cheat ? "red" : "yellow", "limbo-coordmod",
				player.getName().getString(), text, details.toString());
	}


	private static List<Doctor.ModEntry> legacyMods(ClientMods.Report report) {
		List<Doctor.ModEntry> out = new ArrayList<>(report.mods().size());
		for (String entry : report.mods()) {
			String id = ClientMods.idOf(entry);
			String version = entry.length() > id.length() ? entry.substring(id.length()).trim() : "";
			out.add(new Doctor.ModEntry(id, version, "", ""));
		}
		return out;
	}

	private record Extra(String id, String version, String sha1, String risk, boolean cheat,
			boolean nested) {
	}

	private record Mismatch(String id, String have, String canon) {
	}

	private record Diff(List<Extra> extra, List<String> missing, List<Mismatch> mismatch,
			List<String> hidden, boolean canonKnown) {

		String fingerprint(String source) {
			TreeSet<String> parts = new TreeSet<>();
			for (Extra e : extra) {
				parts.add("e:" + e.id() + ":" + e.version());
			}
			for (String m : missing) {
				parts.add("s:" + m);
			}
			for (Mismatch m : mismatch) {
				parts.add("v:" + m.id() + ":" + m.have());
			}
			for (String h : hidden) {
				parts.add("h:" + h);
			}
			return source + "|" + canonKnown + "|" + String.join(";", parts);
		}
	}

	private static Diff diff(ServerPlayerEntity player, List<Doctor.ModEntry> mods,
			boolean havePassport) {
		Map<String, CanonicalPack.CanonMod> canon = CanonicalPack.mods();
		boolean canonKnown = !canon.isEmpty();

		List<Extra> extra = new ArrayList<>();
		List<Mismatch> mismatch = new ArrayList<>();
		Set<String> playerIds = new HashSet<>();

		for (Doctor.ModEntry mod : mods) {
			playerIds.add(mod.id());
			if (mod.nested()) {
				continue;
			}
			CanonicalPack.CanonMod canonMod = canon.get(mod.id());
			if (canonMod == null) {
				ClientMods.Risk risk = ClientMods.riskOf(mod.id());
				boolean cheat = Doctor.isCheatId(mod.id());
				if (canonKnown || risk != null || cheat) {
					extra.add(new Extra(mod.id(), mod.version(), mod.sha1(),
							risk == null ? "" : risk.label(), cheat, mod.nested()));
				}
			} else if (!canonMod.version().isEmpty() && !mod.version().isEmpty()
					&& !canonMod.version().equalsIgnoreCase(mod.version())) {
				mismatch.add(new Mismatch(mod.id(), mod.version(), canonMod.version()));
			}
		}

		List<String> missing = new ArrayList<>();
		if (canonKnown) {
			for (Map.Entry<String, CanonicalPack.CanonMod> e : canon.entrySet()) {
				if (!e.getValue().optional() && !playerIds.contains(e.getKey())) {
					missing.add(e.getKey());
				}
			}
		}

		List<String> hidden = havePassport ? Doctor.hiddenMods(player) : List.of();

		return new Diff(extra, missing, mismatch, hidden, canonKnown);
	}

	private static String composition(List<Doctor.ModEntry> mods) {
		TreeSet<String> parts = new TreeSet<>();
		for (Doctor.ModEntry m : mods) {
			parts.add(m.id() + ":" + m.version() + ":" + m.sha1());
		}
		return String.join(";", parts);
	}


	private static String fullJson(ServerPlayerEntity player, Doctor.Passport passport,
			String source, boolean truncated, List<Doctor.ModEntry> mods) {
		StringBuilder sb = new StringBuilder(1024);
		sb.append('{');
		field(sb, "ts", LocalDateTime.now().format(TS)).append(',');
		field(sb, "type", "full").append(',');
		field(sb, "player", player.getName().getString()).append(',');
		field(sb, "uuid", player.getUuid().toString()).append(',');
		field(sb, "source", source).append(',');
		if (passport != null) {
			field(sb, "mc", passport.mcVersion()).append(',');
			field(sb, "loader", passport.loaderVersion()).append(',');
			field(sb, "mod", passport.modVersion()).append(',');
			sb.append("\"schema\":").append(passport.schema()).append(',');
		}
		sb.append("\"truncated\":").append(truncated).append(',');
		sb.append("\"mods\":[");
		for (int i = 0; i < mods.size(); i++) {
			Doctor.ModEntry m = mods.get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append('{');
			field(sb, "id", m.id()).append(',');
			field(sb, "ver", m.version());
			if (!m.sha1().isEmpty()) {
				field(sb.append(','), "sha1", m.sha1());
			}
			if (!m.parent().isEmpty()) {
				field(sb.append(','), "parent", m.parent());
			}
			sb.append('}');
		}
		sb.append(']');
		if (passport != null) {
			sb.append(",\"packs\":");
			jsonStrings(sb, passport.packs());
			sb.append(",\"channels\":");
			List<String> channels = new ArrayList<>();
			for (Identifier id : Doctor.channels(player)) {
				channels.add(id.toString());
			}
			jsonStrings(sb, channels);
		}
		return sb.append('}').toString();
	}

	private static String diffJson(ServerPlayerEntity player, String source, Diff diff) {
		StringBuilder sb = new StringBuilder(512);
		sb.append('{');
		field(sb, "ts", LocalDateTime.now().format(TS)).append(',');
		field(sb, "type", "diff").append(',');
		field(sb, "player", player.getName().getString()).append(',');
		field(sb, "uuid", player.getUuid().toString()).append(',');
		field(sb, "source", source).append(',');
		field(sb, "canon", diff.canonKnown() ? CanonicalPack.label() : "none").append(',');
		sb.append("\"extra\":[");
		for (int i = 0; i < diff.extra().size(); i++) {
			Extra e = diff.extra().get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append('{');
			field(sb, "id", e.id()).append(',');
			field(sb, "ver", e.version()).append(',');
			if (!e.sha1().isEmpty()) {
				field(sb, "sha1", e.sha1()).append(',');
			}
			field(sb, "risk", e.risk()).append(',');
			sb.append("\"cheat\":").append(e.cheat()).append(',');
			sb.append("\"nested\":").append(e.nested());
			sb.append('}');
		}
		sb.append("],\"missing\":");
		jsonStrings(sb, diff.missing());
		sb.append(",\"mismatch\":[");
		for (int i = 0; i < diff.mismatch().size(); i++) {
			Mismatch m = diff.mismatch().get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append('{');
			field(sb, "id", m.id()).append(',');
			field(sb, "have", m.have()).append(',');
			field(sb, "canon", m.canon());
			sb.append('}');
		}
		sb.append("],\"hidden\":");
		jsonStrings(sb, diff.hidden());
		return sb.append('}').toString();
	}

	private static void jsonStrings(StringBuilder sb, List<String> items) {
		sb.append('[');
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append('"').append(escape(items.get(i))).append('"');
		}
		sb.append(']');
	}

	private static StringBuilder field(StringBuilder sb, String name, String value) {
		return sb.append('"').append(name).append("\":\"")
				.append(escape(value == null ? "" : value)).append('"');
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}

	private static void writeLine(String line) {
		synchronized (LOCK) {
			try {
				if (writer == null) {
					Files.createDirectories(FILE.getParent());
					writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				}
				writer.write(line);
				writer.newLine();
				writer.flush();
			} catch (IOException e) {
				InstantLore.LOGGER.warn("[limbo-audit] file cannot be written to disk", e);
			}
		}
	}
}
