package com.rufana.instantlore.server;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class XaeroLeakLog {

	public static final String REPORT_TYPE = "xaero-leak";
	public static final String GUARD_TYPE = "xaero-guard";
	public static final String GUARD_BLIND_TYPE = "xaero-guard-blind";

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve(InstantLore.MOD_ID).resolve("xaero-leaks.jsonl");

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final Object LOCK = new Object();
	private static BufferedWriter writer;

	private static final ConcurrentHashMap<UUID, Set<String>> TG_ALERTED = new ConcurrentHashMap<>();

	private XaeroLeakLog() {
	}

	public static void register() {
		LoreReportBus.subscribe((player, type, data) -> {
			if (REPORT_TYPE.equals(type)) {
				onLeak(player, data);
			} else if (GUARD_TYPE.equals(type)) {
				onGuard(player, data);
			} else if (GUARD_BLIND_TYPE.equals(type)) {
				onGuardBlind(player, data);
			}
		});
	}

	public static void forget(UUID uuid) {
		if (uuid != null) {
			TG_ALERTED.remove(uuid);
		}
	}

	private static void onLeak(net.minecraft.server.network.ServerPlayerEntity player,
			JsonObject data) {
		String nick = player.getName().getString();
		String cls = str(data, "class");
		String dim = str(data, "dim");
		String bucket = str(data, "bucket");
		String branch = str(data, "branch");
		String server = str(data, "server");
		int tiles = intval(data, "tiles");
		int sec = intval(data, "sec");
		String mod = str(data, "mod");
		String xaero = str(data, "xaero");
		int guardBlocked = intval(data, "guardBlocked");
		int saveBlocked = intval(data, "saveBlocked");
		int cacheBlocked = intval(data, "cacheBlocked");
		boolean guardBlind = data.has("guardBlind") && data.get("guardBlind").getAsBoolean();
		String sample = str(data, "sample");
		String exts = str(data, "exts");

		JsonObject row = new JsonObject();
		row.addProperty("ts", LocalDateTime.now().format(TS));
		row.addProperty("player", nick);
		row.addProperty("uuid", player.getUuidAsString());
		row.addProperty("class", cls);
		row.addProperty("branch", branch);
		row.addProperty("server", server);
		row.addProperty("dim", dim);
		row.addProperty("bucket", bucket);
		row.addProperty("tiles", tiles);
		row.addProperty("sec", sec);
		row.addProperty("mod", mod);
		row.addProperty("xaero", xaero);
		row.addProperty("guardBlocked", guardBlocked);
		row.addProperty("saveBlocked", saveBlocked);
		row.addProperty("cacheBlocked", cacheBlocked);
		row.addProperty("guardBlind", guardBlind);
		row.addProperty("sample", sample);
		row.addProperty("exts", exts);
		writeLine(row.toString());

		Set<String> alerted = TG_ALERTED.computeIfAbsent(player.getUuid(),
				k -> ConcurrentHashMap.newKeySet());
		if (!alerted.add(dim + "|" + cls)) {
			return;
		}

		boolean limbo = "LIMBO".equals(cls);
		String text = limbo
				? "Xaero пишет в лимбо-ведро (регресс слепоты): " + human(dim)
				: "Карта Xaero ушла в " + cls + ": " + human(dim);
		String guardLine = guardBlind ? "ОСЛЕП (рефлексия назначения сломалась)"
				: "writeMap=" + guardBlocked + ", сейв-флаш=" + saveBlocked + ", кэш=" + cacheBlocked;
		String voronka = voronkaLine(exts, sample);
		String details = "ветка=" + branch + "  ведро=" + bucket
				+ "\nтайлов за сессию~" + tiles + ", +" + sec + "с после захода"
				+ "\nгейт: " + guardLine + voronka
				+ "\nмод=" + mod + "  xaero=" + xaero + "  сервер=" + server;

		AlertSink.send(limbo ? "red" : "yellow", "xaero-leak", nick, text, details);
	}

	private static void onGuard(net.minecraft.server.network.ServerPlayerEntity player,
			JsonObject data) {
		String nick = player.getName().getString();
		int blocked = intval(data, "blocked");
		int saveBlocked = intval(data, "saveBlocked");
		int cacheBlocked = intval(data, "cacheBlocked");
		String mod = str(data, "mod");
		String xaero = str(data, "xaero");

		JsonObject row = new JsonObject();
		row.addProperty("ts", LocalDateTime.now().format(TS));
		row.addProperty("event", "guard");
		row.addProperty("player", nick);
		row.addProperty("uuid", player.getUuidAsString());
		row.addProperty("blocked", blocked);
		row.addProperty("saveBlocked", saveBlocked);
		row.addProperty("cacheBlocked", cacheBlocked);
		row.addProperty("mod", mod);
		row.addProperty("xaero", xaero);
		writeLine(row.toString());

		Set<String> alerted = TG_ALERTED.computeIfAbsent(player.getUuid(),
				k -> ConcurrentHashMap.newKeySet());
		if (!alerted.add("GUARD")) {
			return;
		}

		AlertSink.send("yellow", "xaero-guard", nick,
				"Xaero write-guard поймал гонку входа в лимбо (фикс слепоты жив)",
				"отменено: writeMap~" + blocked + ", сейв-флаш~" + saveBlocked + ", кэш~" + cacheBlocked
						+ "\nмод=" + mod + "  xaero=" + xaero);
	}

	private static void onGuardBlind(net.minecraft.server.network.ServerPlayerEntity player,
			JsonObject data) {
		String nick = player.getName().getString();
		String reason = str(data, "reason");
		String mod = str(data, "mod");
		String xaero = str(data, "xaero");

		JsonObject row = new JsonObject();
		row.addProperty("ts", LocalDateTime.now().format(TS));
		row.addProperty("event", "guard-blind");
		row.addProperty("player", nick);
		row.addProperty("uuid", player.getUuidAsString());
		row.addProperty("reason", reason);
		row.addProperty("mod", mod);
		row.addProperty("xaero", xaero);
		writeLine(row.toString());

		Set<String> alerted = TG_ALERTED.computeIfAbsent(player.getUuid(),
				k -> ConcurrentHashMap.newKeySet());
		if (!alerted.add("GUARD-BLIND")) {
			return;
		}

		AlertSink.send("red", "xaero-guard-blind", nick,
				"Xaero write-guard ОСЛЕП: рефлексия назначения сломалась под Xaero " + xaero
						+ " — защита лимбо-записи выключена, нужна перепроверка миксина",
				"причина=" + reason + "\nмод=" + mod + "  xaero=" + xaero);
	}

	private static String voronkaLine(String exts, String sample) {
		StringBuilder sb = new StringBuilder();
		if (sample != null && !"?".equals(sample)) {
			sb.append("\nфайл=").append(sample);
		}
		if (exts == null || "?".equals(exts) || exts.isEmpty()) {
			return sb.toString();
		}
		java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
		for (String raw : exts.split(",")) {
			String e = raw.trim();
			if (e.isEmpty()) {
				continue;
			}
			switch (e) {
				case "xwmc", "xwmc.outdated" -> names.add("КЭШ зум-уровней (saveCacheTextures)");
				case "zip" -> names.add("нормальный тайл (saveRegion)");
				case "mca" -> names.add("ваниль-экспорт региона");
				case "txt" -> names.add("метка/вейпоинт");
				case "owner" -> names.add("realms-владелец");
				default -> names.add("НЕИЗВЕСТНАЯ воронка (." + e + ")");
			}
		}
		if (!names.isEmpty()) {
			sb.append("\nворонки: ").append(String.join(", ", names));
		}
		return sb.toString();
	}

	private static String human(String dim) {
		return switch (dim) {
			case "null", "dim%0" -> "оверворлд";
			case "DIM-1", "dim%-1" -> "ад";
			case "DIM1", "dim%1" -> "энд";
			default -> dim;
		};
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "?";
	}

	private static int intval(JsonObject o, String k) {
		try {
			return o.has(k) ? o.get(k).getAsInt() : 0;
		} catch (Exception e) {
			return 0;
		}
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
				InstantLore.LOGGER.warn("[xaero-leak] file cannot be written to disk", e);
			}
		}
	}
}
