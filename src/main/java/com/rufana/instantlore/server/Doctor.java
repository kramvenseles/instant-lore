package com.rufana.instantlore.server;

import com.rufana.instantlore.net.LorePassportPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Doctor {

	public record ModEntry(String id, String version, String sha1, String parent) {

		public boolean nested() {
			return !parent.isEmpty();
		}
	}

	public record Passport(
			String mcVersion, String loaderVersion, String modVersion, int schema,
			List<ModEntry> mods, List<String> packs,
			int totalMods, int totalPacks, long receivedAt) {

		public boolean truncated() {
			return totalMods > mods.size() || totalPacks > packs.size();
		}

		public long hashed() {
			return mods.stream().filter(m -> !m.sha1().isEmpty()).count();
		}

		public long topLevel() {
			return mods.stream().filter(m -> !m.nested()).count();
		}

		public boolean reportsNested() {
			return schema >= 2;
		}
	}

	public enum Verdict {
		CHEAT("чит"),
		SEE_THROUGH("сквозной"),
		HIDDEN("скрыт от доклада"),
		UNKNOWN("неизвестен");

		private final String label;

		Verdict(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final Set<String> CHEAT_IDS = Set.of(
			"wurst", "meteor-client", "meteorclient", "baritone", "baritone-api",
			"impact", "aristois", "rusherhack", "liquidbounce", "future", "sigma",
			"konas", "seppuku", "inertia", "kamiblue", "catclient",
			"xray", "advancedxray", "xrayultimate", "xraymod", "oresee",
			"elytrabot", "autofish", "killaura", "reachhack");


	private static final Map<String, Set<String>> CHANNEL_OWNERS = new LinkedHashMap<>();

	static {
		CHANNEL_OWNERS.put("journeymap:perm_req", Set.of("journeymap"));
		CHANNEL_OWNERS.put("voxelmap:settings", Set.of("voxelmap"));
		CHANNEL_OWNERS.put("worldinfo:world_id", Set.of("voxelmap", "journeymap"));
		CHANNEL_OWNERS.put("xaerominimap:main", Set.of("xaerominimap"));
		CHANNEL_OWNERS.put("xaeroworldmap:main", Set.of("xaeroworldmap"));
		CHANNEL_OWNERS.put("xaerolib:main", Set.of("xaerolib"));
		CHANNEL_OWNERS.put("ftbchunks:sync_tx", Set.of("ftbchunks"));
		CHANNEL_OWNERS.put("jei:channel", Set.of("jei"));
		CHANNEL_OWNERS.put("roughlyenoughitems:sync_displays", Set.of("roughlyenoughitems"));
		CHANNEL_OWNERS.put("roughlyenoughitems:request_tags_s2c", Set.of("roughlyenoughitems"));
		CHANNEL_OWNERS.put("servux:litematics", Set.of("litematica"));
		CHANNEL_OWNERS.put("architectury:spawn_entity_packet", Set.of("architectury"));
		CHANNEL_OWNERS.put("voicechat:secret", Set.of("voicechat"));
	}

	private static final ConcurrentHashMap<UUID, Passport> PASSPORTS = new ConcurrentHashMap<>();

	private Doctor() {
	}


	public static void record(UUID player, LorePassportPayload payload) {
		if (player == null || payload == null) {
			return;
		}
		String[] env = payload.env().split(LorePassportPayload.SEP, -1);
		List<ModEntry> mods = new ArrayList<>(payload.mods().size());
		for (String line : payload.mods()) {
			String[] parts = line.split(LorePassportPayload.SEP, -1);
			mods.add(new ModEntry(
					parts.length > 0 ? parts[0].toLowerCase(Locale.ROOT) : "",
					parts.length > 1 ? parts[1] : "",
					parts.length > 2 ? parts[2] : "",
					parts.length > 3 ? parts[3].toLowerCase(Locale.ROOT) : ""));
		}
		PASSPORTS.put(player, new Passport(
				env.length > 0 ? env[0] : "?",
				env.length > 1 ? env[1] : "?",
				env.length > 2 ? env[2] : "?",
				schemaOf(env),
				List.copyOf(mods), List.copyOf(payload.packs()),
				payload.totalMods(), payload.totalPacks(), System.currentTimeMillis()));
	}

	private static int schemaOf(String[] env) {
		if (env.length < 4) {
			return 1;
		}
		try {
			return Integer.parseInt(env[3].trim());
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public static Passport passport(UUID player) {
		return player == null ? null : PASSPORTS.get(player);
	}

	public static boolean isCheatId(String id) {
		return id != null && CHEAT_IDS.contains(id);
	}

	public static void forget(UUID player) {
		if (player != null) {
			PASSPORTS.remove(player);
			ALERTED.remove(player);
		}
	}

	private static final ConcurrentHashMap<UUID, Set<String>> ALERTED = new ConcurrentHashMap<>();

	public static void examine(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		try {
			Passport passport = passport(player.getUuid());
			if (passport == null) {
				return;
			}
			Set<String> already = ALERTED.computeIfAbsent(
					player.getUuid(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
			String nick = player.getName().getString();

			Map<ModEntry, Verdict> flagged = flagged(passport);
			for (Map.Entry<ModEntry, Verdict> entry : flagged.entrySet()) {
				if (entry.getValue() != Verdict.CHEAT) {
					continue;
				}
				ModEntry mod = entry.getKey();
				if (!already.add("cheat:" + mod.id())) {
					continue;
				}
				AlertSink.send("red", "cheat-client", nick,
						"Известный чит-мод в сборке: " + mod.id(),
						"версия: " + mod.version()
								+ (mod.sha1().isEmpty() ? "" : " · sha1: " + mod.sha1()));
			}

			List<String> hidden = hiddenMods(player);
			for (String item : hidden) {
				if (already.add("hidden:" + item)) {
					AlertSink.send("red", "report-mismatch", nick,
							"Доклад не сходится: мод скрыт, а его канал объявлен",
							item);
				}
			}
		} catch (Throwable e) {
			com.rufana.instantlore.InstantLore.LOGGER.warn("[doctor] inspection failed", e);
		}
	}

	public static void examineLegacy(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		try {
			if (passport(player.getUuid()) != null) {
				return;
			}
			ClientMods.Report report = ClientMods.reported(player.getUuid());
			if (report == null) {
				return;
			}
			Set<String> already = ALERTED.computeIfAbsent(
					player.getUuid(), k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
			String nick = player.getName().getString();
			for (String entry : report.mods()) {
				String id = ClientMods.idOf(entry);
				if (!CHEAT_IDS.contains(id) || !already.add("cheat:" + id)) {
					continue;
				}
				String version = entry.length() > id.length() ? entry.substring(id.length()).trim() : "";
				AlertSink.send("red", "cheat-client", nick,
						"Известный чит-мод в сборке: " + id,
						(version.isEmpty() ? "" : "версия: " + version + " · ")
								+ "источник: старый доклад модов (паспорта нет — клиент не обновлён)");
			}
		} catch (Throwable e) {
			com.rufana.instantlore.InstantLore.LOGGER.warn("[doctor] inspection from stale report "
					+ "failed", e);
		}
	}


	public static Map<ModEntry, Verdict> flagged(Passport passport) {
		Map<ModEntry, Verdict> out = new LinkedHashMap<>();
		if (passport == null) {
			return out;
		}
		for (ModEntry mod : passport.mods()) {
			if (CHEAT_IDS.contains(mod.id())) {
				out.put(mod, Verdict.CHEAT);
			}
		}
		for (ModEntry mod : passport.mods()) {
			if (!out.containsKey(mod) && ClientMods.riskOf(mod.id()) != null) {
				out.put(mod, Verdict.SEE_THROUGH);
			}
		}
		return out;
	}

	public static List<String> hiddenMods(ServerPlayerEntity player) {
		List<String> out = new ArrayList<>();
		if (player == null) {
			return out;
		}
		Passport passport = passport(player.getUuid());
		if (passport == null || !passport.reportsNested() || passport.truncated()) {
			return out;
		}
		Set<String> declared = new java.util.HashSet<>();
		for (ModEntry mod : passport.mods()) {
			declared.add(mod.id());
		}
		for (Identifier channel : channels(player)) {
			Set<String> owners = CHANNEL_OWNERS.get(channel.toString());
			if (owners == null || owners.stream().anyMatch(declared::contains)) {
				continue;
			}
			out.add(String.join(" или ", new java.util.TreeSet<>(owners))
					+ " (канал " + channel + ")");
		}
		return out;
	}

	public static Set<Identifier> channels(ServerPlayerEntity player) {
		try {
			return player == null ? Set.of() : ServerPlayNetworking.getSendable(player);
		} catch (Throwable e) {
			return Set.of();
		}
	}
}
