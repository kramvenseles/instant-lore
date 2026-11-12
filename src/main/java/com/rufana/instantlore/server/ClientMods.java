package com.rufana.instantlore.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMods {

	public enum Risk {
		MAP("карта мимо Xaero"),
		COORDS("координаты мимо F3"),
		SIGHT("видимость сквозь мир");

		private final String label;

		Risk(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final Map<String, Risk> KNOWN_RISK = new LinkedHashMap<>();

	static {
		KNOWN_RISK.put("journeymap", Risk.MAP);
		KNOWN_RISK.put("voxelmap", Risk.MAP);
		KNOWN_RISK.put("antiqueatlas", Risk.MAP);
		KNOWN_RISK.put("map_atlases", Risk.MAP);
		KNOWN_RISK.put("bobby", Risk.MAP);
		KNOWN_RISK.put("distanthorizons", Risk.MAP);
		KNOWN_RISK.put("ftbchunks", Risk.MAP);
		KNOWN_RISK.put("journeymapclient", Risk.MAP);

		KNOWN_RISK.put("sodium-extra", Risk.COORDS);
		KNOWN_RISK.put("sodiumextra", Risk.COORDS);
		KNOWN_RISK.put("coordinateshud", Risk.COORDS);
		KNOWN_RISK.put("coordinatesdisplay", Risk.COORDS);
		KNOWN_RISK.put("moreoverlays", Risk.COORDS);
		KNOWN_RISK.put("waypoints", Risk.COORDS);
		KNOWN_RISK.put("wamp", Risk.COORDS);

		KNOWN_RISK.put("xray", Risk.SIGHT);
		KNOWN_RISK.put("xraymod", Risk.SIGHT);
		KNOWN_RISK.put("advancedxray", Risk.SIGHT);
		KNOWN_RISK.put("freecam", Risk.SIGHT);
		KNOWN_RISK.put("fullbright", Risk.SIGHT);
		KNOWN_RISK.put("gammautils", Risk.SIGHT);
		KNOWN_RISK.put("nightvision", Risk.SIGHT);
		KNOWN_RISK.put("wi_zoom", Risk.SIGHT);
		KNOWN_RISK.put("entityculling", Risk.SIGHT);
		KNOWN_RISK.put("wthit", Risk.SIGHT);
		KNOWN_RISK.put("jade", Risk.SIGHT);
	}

	public record Report(List<String> mods, int total) {

		public boolean truncated() {
			return total > mods.size();
		}
	}

	private static final ConcurrentHashMap<UUID, Report> REPORTED = new ConcurrentHashMap<>();

	private ClientMods() {
	}

	public static void record(UUID player, List<String> mods, int total) {
		if (player != null && mods != null) {
			REPORTED.put(player, new Report(List.copyOf(mods), Math.max(total, mods.size())));
		}
	}

	public static Report reported(UUID player) {
		return player == null ? null : REPORTED.get(player);
	}

	public static void forget(UUID player) {
		if (player != null) {
			REPORTED.remove(player);
		}
	}

	public static Risk riskOf(String entry) {
		return KNOWN_RISK.get(idOf(entry));
	}

	public static String idOf(String entry) {
		if (entry == null) {
			return "";
		}
		int space = entry.indexOf(' ');
		return (space < 0 ? entry : entry.substring(0, space)).toLowerCase(Locale.ROOT);
	}

	public static boolean isFabricApiModule(String entry) {
		String id = idOf(entry);
		return id.startsWith("fabric-") && !id.equals("fabric-api");
	}

	public static List<String> risky(Report report) {
		List<String> out = new ArrayList<>();
		if (report == null) {
			return out;
		}
		for (String entry : report.mods()) {
			if (riskOf(entry) != null) {
				out.add(entry);
			}
		}
		return out;
	}
}
