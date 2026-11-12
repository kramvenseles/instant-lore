package com.rufana.instantlore.update;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class UpdateLog {

	private static final long MAX_BYTES = 256 * 1024;

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");


	public static final String NO_JAR = "no_jar";
	public static final String CDN_SILENT = "cdn_silent";
	public static final String UP_TO_DATE = "up_to_date";
	public static final String STAGED_READY = "staged_ready";
	public static final String DOWNLOADED = "downloaded";
	public static final String MANIFEST_BAD = "manifest_bad";
	public static final String DOWNLOAD_FAILED = "download_failed";
	public static final String WRITE_FAILED = "write_failed";
	public static final String CHECK_FAILED = "check_failed";
	public static final String SWAP_SPAWNED = "swap_spawned";
	public static final String SWAP_SPAWN_FAILED = "swap_spawn_failed";
	public static final String APPLIED = "applied";

	private UpdateLog() {
	}

	private static Path dir() {
		return SwapLauncher.UPDATE_DIR;
	}

	private static Path file() {
		return dir().resolve("updater.log");
	}

	private static Path lastFile() {
		return dir().resolve("last.txt");
	}

	public static void note(String code, String detail) {
		String masked = mask(detail);
		line(code + (masked.isEmpty() ? "" : " — " + masked));
		try {
			Files.createDirectories(dir());
			Files.writeString(lastFile(), code + "|" + masked, StandardCharsets.UTF_8);
		} catch (Throwable ignored) {
		}
	}

	public static void line(String message) {
		try {
			Files.createDirectories(dir());
			rotateIfHuge();
			Files.writeString(file(),
					"[" + LocalDateTime.now().format(STAMP) + "] " + message + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Throwable ignored) {
		}
	}

	public static String last() {
		try {
			return Files.isRegularFile(lastFile())
					? Files.readString(lastFile(), StandardCharsets.UTF_8).trim()
					: "";
		} catch (Throwable t) {
			return "";
		}
	}

	public static String tail(Path path, int maxBytes) {
		try {
			if (!Files.isRegularFile(path)) {
				return "";
			}
			List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
			StringBuilder sb = new StringBuilder();
			for (int i = lines.size() - 1; i >= 0; i--) {
				String candidate = lines.get(i);
				if (sb.length() + candidate.length() + 1 > maxBytes) {
					break;
				}
				sb.insert(0, candidate + "\n");
			}
			return mask(sb.toString());
		} catch (Throwable t) {
			return "";
		}
	}

	public static Path swapLog() {
		return dir().resolve("swap.log");
	}

	public static String mask(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("(?i)([\\\\/])(Users|home)([\\\\/])[^\\\\/\\s]+", "$1$2$3~");
	}

	private static void rotateIfHuge() {
		try {
			if (Files.isRegularFile(file()) && Files.size(file()) > MAX_BYTES) {
				Files.move(file(), dir().resolve("updater.log.1"), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Throwable ignored) {
		}
	}
}
