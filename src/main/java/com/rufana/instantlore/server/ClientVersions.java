package com.rufana.instantlore.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.ContentSource;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientVersions {

	private static final ConcurrentHashMap<UUID, String> REPORTED = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<UUID, UpdateState> UPDATE_STATE = new ConcurrentHashMap<>();

	public record UpdateState(String stagedVersion, boolean swappable, int swapAttempts) {

		public boolean swapFailing() {
			return stagedVersion != null && swapAttempts >= 2;
		}
	}

	private static final ConcurrentHashMap<UUID, Diag> DIAG = new ConcurrentHashMap<>();

	public record Diag(String code, String detail, String env, String swapTail,
			String updaterTail, long at) {
	}

	public static void recordDiag(UUID player, String code, String detail, String env,
			String swapTail, String updaterTail) {
		if (player != null) {
			DIAG.put(player, new Diag(code, detail, env, swapTail, updaterTail,
					System.currentTimeMillis()));
		}
	}

	public static Diag diag(UUID player) {
		return player == null ? null : DIAG.get(player);
	}

	private static volatile String cdnTarget = null;

	private ClientVersions() {
	}

	public static void record(UUID player, String version) {
		if (player != null && version != null && !version.isBlank()) {
			REPORTED.put(player, version);
		}
	}

	public static void recordUpdateState(UUID player, String stagedVersion, boolean swappable,
			int swapAttempts) {
		if (player == null) {
			return;
		}
		String staged = (stagedVersion == null || stagedVersion.isBlank()) ? null : stagedVersion;
		UPDATE_STATE.put(player, new UpdateState(staged, swappable, swapAttempts));
	}

	public static UpdateState updateState(UUID player) {
		return player == null ? null : UPDATE_STATE.get(player);
	}

	public static void forget(UUID player) {
		if (player != null) {
			REPORTED.remove(player);
			UPDATE_STATE.remove(player);
			DIAG.remove(player);
		}
	}

	public static String reported(UUID player) {
		return player == null ? null : REPORTED.get(player);
	}

	public static boolean meetsMinimum(UUID player, int... min) {
		return meetsMinimum(reported(player), min);
	}

	static boolean meetsMinimum(String reported, int... min) {
		if (reported == null) {
			return false;
		}

		String[] parts = reported.trim().replaceAll("[^0-9.]", "").split("\\.");

		int meaningful = 0;
		for (String p : parts) {
			if (!p.isEmpty()) {
				meaningful++;
			}
		}

		if (meaningful < 2) {
			return false;
		}

		for (int i = 0; i < min.length; i++) {
			int have = i < parts.length ? parseIntOrZero(parts[i]) : 0;
			if (have != min[i]) {
				return have > min[i];
			}
		}

		return true;
	}

	private static int parseIntOrZero(String s) {
		try {
			return s.isEmpty() ? 0 : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static String target() {
		return cdnTarget;
	}

	public static void refreshTarget() {
		Thread worker = new Thread(() -> {
			try {
				String doc = ContentSource.fetchClientVersion();
				if (doc == null) {
					cdnTarget = null;
					return;
				}

				JsonObject json = JsonParser.parseString(doc).getAsJsonObject();
				if (json.has("version")) {
					cdnTarget = json.get("version").getAsString();
				}
			} catch (Throwable e) {
				InstantLore.LOGGER.debug("Failed to reload reference client version", e);
			}
		}, "instantlore-target-version");
		worker.setDaemon(true);
		worker.start();
	}
}
