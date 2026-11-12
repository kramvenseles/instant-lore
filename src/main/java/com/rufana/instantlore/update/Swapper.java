package com.rufana.instantlore.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class Swapper {

	private static final long LOCK_WAIT_STEP_MS = 500;

	private static final long DEFAULT_LOCK_WAIT_SECONDS = 60;

	private static final long LOCK_STALE_MS = 24L * 60 * 60 * 1000;

	private Swapper() {
	}

	public static void main(String[] args) {
		if (args.length < 4) {
			log("неверные аргументы: ожидались <oldJar> <stagedJar> <destJar> <sha256> [ждатьСек]");
			return;
		}

		Path oldJar = Paths.get(args[0]);
		Path staged = Paths.get(args[1]);
		Path dest = Paths.get(args[2]);
		String expected = args[3].toLowerCase();
		long waitSeconds = args.length > 4 ? parseSeconds(args[4]) : DEFAULT_LOCK_WAIT_SECONDS;

		Path lock = staged.resolveSibling("swap.lock");
		if (!acquire(lock)) {
			log("подмену уже ведёт другой хелпер — выхожу");
			return;
		}

		try {
			run(oldJar, staged, dest, expected, waitSeconds);
		} catch (Throwable t) {
			log("необработанная ошибка: " + t);
		} finally {
			try {
				Files.deleteIfExists(lock);
			} catch (IOException e) {
				log("не удалось снять замок " + lock + ": " + e);
			}
		}
	}

	private static long parseSeconds(String raw) {
		try {
			long parsed = Long.parseLong(raw.trim());
			return parsed > 0 ? parsed : DEFAULT_LOCK_WAIT_SECONDS;
		} catch (NumberFormatException e) {
			return DEFAULT_LOCK_WAIT_SECONDS;
		}
	}

	private static boolean acquire(Path lock) {
		try {
			Files.createDirectories(lock.getParent());
			try {
				Files.createFile(lock);
				return true;
			} catch (java.nio.file.FileAlreadyExistsException exists) {
				long age = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
				if (age < LOCK_STALE_MS) {
					return false;
				}
				log("замок протух (" + age / 1000 + " с) — беру его себе");
				Files.setLastModifiedTime(lock, java.nio.file.attribute.FileTime.fromMillis(
						System.currentTimeMillis()));
				return true;
			}
		} catch (IOException e) {
			log("замок недоступен (" + e + ") — продолжаю без него");
			return true;
		}
	}

	private static void run(Path oldJar, Path staged, Path dest, String expected, long waitSeconds)
			throws IOException {
		log("старт: old=" + oldJar + " staged=" + staged + " dest=" + dest
				+ " (ждать лок до " + waitSeconds + " с)");

		if (!Files.exists(staged)) {
			log("staged-jar отсутствует — применять нечего, выход");
			return;
		}

		String stagedSha = sha256(staged);
		if (!stagedSha.equals(expected)) {
			log("sha256 staged не сходится (" + stagedSha + " != " + expected + ") — отмена, старый не тронут");
			return;
		}

		Path backup = null;
		if (Files.exists(oldJar)) {
			backup = oldJar.resolveSibling(oldJar.getFileName() + ".bak");
			try {
				Files.copy(oldJar, backup, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.COPY_ATTRIBUTES);
				log("бэкап старого: " + backup);
			} catch (IOException e) {
				log("не удалось снять бэкап (" + e + ") — отмена, старый не тронут");
				return;
			}
		}

		if (Files.exists(oldJar) && !waitDeletable(oldJar, waitSeconds)) {
			log("старый jar не освободился за отведённое время — отмена, ничего не тронуто");
			return;
		}

		try {
			Files.createDirectories(dest.getParent());
			Files.copy(staged, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log("копирование staged→dest не удалось: " + e);
			restore(backup, oldJar, dest);
			return;
		}

		String destSha = sha256(dest);
		if (!destSha.equals(expected)) {
			log("sha256 dest после копирования не сходится (" + destSha + ") — откат к старому");
			restore(backup, oldJar, dest);
			return;
		}

		log("успех: " + dest + " применён, sha256 совпал; staged уберётся при следующем старте, бэкап: " + backup);
	}

	private static boolean waitDeletable(Path path, long waitSeconds) {
		long attempts = Math.max(1, waitSeconds * 1000 / LOCK_WAIT_STEP_MS);

		long everyNth = attempts > 1000 ? 600 : 20;

		for (long attempt = 1; attempt <= attempts; attempt++) {
			try {
				Files.delete(path);
				return true;
			} catch (IOException stillLocked) {
				if (attempt == 1 || attempt % everyNth == 0) {
					log("старый jar ещё занят, жду… (попытка " + attempt + "/" + attempts + ")");
				}
				try {
					Thread.sleep(LOCK_WAIT_STEP_MS);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
		return false;
	}

	private static void restore(Path backup, Path oldJar, Path dest) {
		if (backup == null || !Files.exists(backup)) {
			log("восстанавливать нечего (бэкапа нет) — mods/ мог остаться без jar, staging сохранён для повтора");
			return;
		}
		try {
			Files.copy(backup, oldJar, StandardCopyOption.REPLACE_EXISTING);
			if (!dest.equals(oldJar)) {
				Files.deleteIfExists(dest);
			}
			log("старый jar восстановлен из бэкапа: " + oldJar);
		} catch (IOException e) {
			log("ВОССТАНОВЛЕНИЕ НЕ УДАЛОСЬ: " + e + " — бэкап цел: " + backup);
		}
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 обязан быть в любой JVM", e);
		}
	}

	private static void log(String message) {
		System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)
				+ "] instantlore-swap: " + message);
	}
}
