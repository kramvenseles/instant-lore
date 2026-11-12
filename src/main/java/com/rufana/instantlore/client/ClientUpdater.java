package com.rufana.instantlore.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.ContentSource;
import com.rufana.instantlore.net.LoreUpdateDiagPayload;
import com.rufana.instantlore.net.LoreUpdateStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.rufana.instantlore.update.SwapLauncher;
import com.rufana.instantlore.update.UpdateLog;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientUpdater {

	private static final String MOD_ID = "instantlore";

	private static final Path UPDATE_DIR = SwapLauncher.UPDATE_DIR;
	private static final Path MARKER = SwapLauncher.MARKER;

	private static final long STOPPING_LOCK_WAIT_SECONDS = 60;

	private static final ExecutorService WORKER =
			Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "instantlore-updater");
				thread.setDaemon(true);
				return thread;
			});

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private ClientUpdater() {
	}


	public static void checkAndStage() {
		if (SwapLauncher.ownJar() == null) {
			UpdateLog.note(UpdateLog.NO_JAR, "мод запущен не из jar в mods/");
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			return;
		}

		WORKER.execute(() -> {
			try {
				stageIfNewer();
			} catch (Throwable t) {
				UpdateLog.note(UpdateLog.CHECK_FAILED, String.valueOf(t));
				InstantLore.LOGGER.warn("Jar auto-update: check failed", t);
			} finally {
				RUNNING.set(false);
				reportState();
			}
		});
	}

	public static void reportState() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}

		client.execute(() -> {
			try {
				if (!ClientPlayNetworking.canSend(LoreUpdateStatePayload.ID)) {
					return;
				}
				SwapLauncher.Staged staged = SwapLauncher.pendingStaged();
				ClientPlayNetworking.send(new LoreUpdateStatePayload(
						staged == null ? "" : staged.version(),
						SwapLauncher.ownJar() != null,
						staged == null ? 0 : staged.attempts()));

				reportDiag();
			} catch (Throwable t) {
				InstantLore.LOGGER.warn("Failed to report auto-update status", t);
			}
		});
	}

	private static void reportDiag() {
		try {
			if (!ClientPlayNetworking.canSend(LoreUpdateDiagPayload.ID)) {
				return;
			}

			String last = UpdateLog.last();
			int bar = last.indexOf('|');
			String code = bar < 0 ? last : last.substring(0, bar);
			String detail = bar < 0 ? "" : last.substring(bar + 1);

			ClientPlayNetworking.send(new LoreUpdateDiagPayload(
					cut(code, LoreUpdateDiagPayload.MAX_CODE),
					cut(detail, LoreUpdateDiagPayload.MAX_DETAIL),
					cut(environment(), LoreUpdateDiagPayload.MAX_ENV),
					cut(UpdateLog.tail(UpdateLog.swapLog(), LoreUpdateDiagPayload.MAX_TAIL),
							LoreUpdateDiagPayload.MAX_TAIL),
					cut(UpdateLog.tail(UPDATE_DIR.resolve("updater.log"), LoreUpdateDiagPayload.MAX_TAIL),
							LoreUpdateDiagPayload.MAX_TAIL)));
		} catch (Throwable t) {
			InstantLore.LOGGER.debug("Failed to send auto-update diagnostics", t);
		}
	}

	private static String environment() {
		Path jar = SwapLauncher.ownJar();
		Path mods = jar == null ? null : jar.getParent();
		return System.getProperty("os.name") + " " + System.getProperty("os.arch")
				+ " · java " + System.getProperty("java.version")
				+ " · jar " + (jar == null ? "нет (не из mods/)" : UpdateLog.mask(jar.toString()))
				+ " · mods " + writable(mods)
				+ " · config " + writable(UPDATE_DIR);
	}

	private static String writable(Path dir) {
		if (dir == null) {
			return "?";
		}
		try {
			return Files.isWritable(dir) ? "rw" : "ro";
		} catch (Throwable t) {
			return "?";
		}
	}

	private static String cut(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(value.length() - max);
	}

	private static void stageIfNewer() throws Exception {
		UpdateLog.line("проверка: своя версия " + SwapLauncher.ownVersion()
				+ ", jar " + UpdateLog.mask(String.valueOf(SwapLauncher.ownJar())));

		String json = ContentSource.fetchClientVersion();
		if (json == null) {
			UpdateLog.note(UpdateLog.CDN_SILENT, ContentSource.lastClientVersionError());
			return;
		}

		String remoteStr;
		String path;
		String sha256;
		long size;
		Version remote;
		try {
			JsonObject doc = JsonParser.parseString(json).getAsJsonObject();
			remoteStr = doc.get("version").getAsString();
			path = doc.get("path").getAsString();
			sha256 = doc.get("sha256").getAsString();
			size = doc.get("size").getAsLong();
			remote = Version.parse(remoteStr);
		} catch (Throwable t) {
			UpdateLog.note(UpdateLog.MANIFEST_BAD, String.valueOf(t));
			return;
		}

		if (remote.compareTo(SwapLauncher.ownVersion()) <= 0) {
			UpdateLog.note(UpdateLog.UP_TO_DATE,
					"своя " + SwapLauncher.ownVersion() + ", на раздаче " + remoteStr);
			return;
		}

		String fileName = MOD_ID + "-" + remoteStr + ".jar";
		if (alreadyStaged(remote, fileName)) {
			UpdateLog.note(UpdateLog.STAGED_READY,
					remoteStr + " лежит на диске, ждёт перезапуска игры");
			InstantLore.LOGGER.debug("Jar auto-update {} already staged", remoteStr);
			return;
		}

		byte[] jar;
		try {
			UpdateLog.line("качаю " + remoteStr + " (" + size + " Б) с " + path);
			jar = ContentSource.fetchAsset(path, sha256, size);
		} catch (Throwable t) {
			UpdateLog.note(UpdateLog.DOWNLOAD_FAILED, remoteStr + ": " + t);
			throw t;
		}

		Path staged = UPDATE_DIR.resolve(fileName);
		try {
			Files.createDirectories(UPDATE_DIR);
			Path part = UPDATE_DIR.resolve(fileName + ".part");
			Files.write(part, jar);
			Files.move(part, staged, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

			JsonObject marker = new JsonObject();
			marker.addProperty("version", remoteStr);
			marker.addProperty("sha256", sha256);
			marker.addProperty("file", fileName);
			Files.writeString(MARKER, marker.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			UpdateLog.note(UpdateLog.WRITE_FAILED,
					UpdateLog.mask(staged.toString()) + ": " + t);
			throw t;
		}

		dropOtherStagedJars(fileName);

		UpdateLog.note(UpdateLog.DOWNLOADED, remoteStr + " скачан и проверен, ждёт перезапуска игры");
		InstantLore.LOGGER.info(
				"Jar auto-update: version {} downloaded and verified, will apply on next game restart",
				remoteStr);
	}


	public static void spawnSwapperIfStaged() {
		SwapLauncher.spawnIfStaged(STOPPING_LOCK_WAIT_SECONDS);
	}


	public static void cleanupIfApplied() {
		pruneOldBackups();
		try {
			JsonObject marker = readMarker();
			if (marker == null) {
				return;
			}

			Version staged = Version.parse(marker.get("version").getAsString());
			if (staged.compareTo(SwapLauncher.ownVersion()) > 0) {
				return;
			}

			Files.deleteIfExists(UPDATE_DIR.resolve(marker.get("file").getAsString()));
			Files.deleteIfExists(MARKER);
			UpdateLog.note(UpdateLog.APPLIED,
					"бежим на " + SwapLauncher.ownVersion() + ", стейджинг очищен");
			InstantLore.LOGGER.info("Jar auto-update: version {} applied, staging cleared",
					marker.get("version").getAsString());
		} catch (Throwable t) {
			InstantLore.LOGGER.debug("Jar auto-update: staging cleanup skipped", t);
		}
	}


	private static boolean alreadyStaged(Version remote, String fileName) {
		JsonObject marker = readMarker();
		if (marker == null) {
			return false;
		}
		try {
			Version stagedVer = Version.parse(marker.get("version").getAsString());
			return stagedVer.compareTo(remote) >= 0
					&& Files.isRegularFile(UPDATE_DIR.resolve(fileName));
		} catch (Exception e) {
			return false;
		}
	}

	private static void pruneOldBackups() {
		try {
			Path jar = SwapLauncher.ownJar();
			if (jar == null) {
				return;
			}
			List<Path> backups;
			try (var list = Files.list(jar.getParent())) {
				backups = list.filter(p -> {
					String n = p.getFileName().toString();
					return n.startsWith(MOD_ID + "-") && n.endsWith(".jar.bak");
				}).sorted(Comparator.comparingLong(ClientUpdater::mtime)).collect(Collectors.toList());
			}
			for (int i = 0; i < backups.size() - 1; i++) {
				try {
					Files.deleteIfExists(backups.get(i));
					InstantLore.LOGGER.info("Jar auto-update: removed old backup {}",
							backups.get(i).getFileName());
				} catch (Exception ignored) {
				}
			}
		} catch (Throwable t) {
			InstantLore.LOGGER.debug("Jar auto-update: old backup cleanup skipped", t);
		}
	}

	private static long mtime(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (Exception e) {
			return 0L;
		}
	}

	private static void dropOtherStagedJars(String keep) {
		try (var list = Files.list(UPDATE_DIR)) {
			list.filter(p -> {
				String n = p.getFileName().toString();
				return n.startsWith(MOD_ID + "-") && (n.endsWith(".jar") || n.endsWith(".jar.part"))
						&& !n.equals(keep);
			}).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (Exception ignored) {
				}
			});
		} catch (Exception ignored) {
		}
	}

	private static JsonObject readMarker() {
		try {
			if (!Files.isRegularFile(MARKER)) {
				return null;
			}
			return JsonParser.parseString(Files.readString(MARKER)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
