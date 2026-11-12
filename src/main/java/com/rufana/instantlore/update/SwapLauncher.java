package com.rufana.instantlore.update;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SwapLauncher {

	private static final String MOD_ID = "instantlore";

	public static final Path UPDATE_DIR =
			FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("update");

	public static final Path MARKER = UPDATE_DIR.resolve("staged.json");

	private SwapLauncher() {
	}

	public record Staged(String version, String sha256, String file, int attempts) {
	}

	public static void spawnIfStaged(long lockWaitSeconds) {
		try {
			Path oldJar = ownJar();
			if (oldJar == null) {
				return;
			}

			Staged staged = readMarker();
			if (staged == null) {
				return;
			}

			if (Version.parse(staged.version()).compareTo(ownVersion()) <= 0) {
				return;
			}

			Path stagedJar = UPDATE_DIR.resolve(staged.file());
			if (!Files.isRegularFile(stagedJar)) {
				UpdateLog.note(UpdateLog.SWAP_SPAWN_FAILED,
						"маркер на " + staged.version() + " есть, а файла нет: "
								+ UpdateLog.mask(stagedJar.toString()));
				return;
			}

			Path dest = oldJar.getParent().resolve(staged.file());

			boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
			String javaBin = Path.of(System.getProperty("java.home"), "bin",
					windows ? "java.exe" : "java").toString();

			ProcessBuilder pb = new ProcessBuilder(
					javaBin, "-cp", stagedJar.toString(),
					"com.rufana.instantlore.update.Swapper",
					oldJar.toString(), stagedJar.toString(), dest.toString(), staged.sha256(),
					Long.toString(lockWaitSeconds));
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(UPDATE_DIR.resolve("swap.log").toFile()));
			pb.start();

			markAttempt(staged);

			UpdateLog.note(UpdateLog.SWAP_SPAWNED, "на " + staged.version() + ", попытка "
					+ (staged.attempts() + 1) + ", ждать лок до " + lockWaitSeconds + " с");
			System.out.println("[instantlore] хелпер подмены на " + staged.version()
					+ " спущен (ждать лок до " + lockWaitSeconds + " с)");
		} catch (Throwable t) {
			UpdateLog.note(UpdateLog.SWAP_SPAWN_FAILED, String.valueOf(t));
			System.out.println("[instantlore] не удалось спустить хелпер подмены: " + t);
		}
	}


	public static Staged readMarker() {
		try {
			if (!Files.isRegularFile(MARKER)) {
				return null;
			}

			String json = Files.readString(MARKER, StandardCharsets.UTF_8);
			String version = text(json, "version");
			String sha256 = text(json, "sha256");
			String file = text(json, "file");

			if (version == null || sha256 == null || file == null) {
				return null;
			}

			return new Staged(version, sha256, file, number(json, "attempts"));
		} catch (Throwable t) {
			return null;
		}
	}

	public static Staged pendingStaged() {
		Staged staged = readMarker();
		if (staged == null) {
			return null;
		}
		try {
			if (Version.parse(staged.version()).compareTo(ownVersion()) <= 0) {
				return null;
			}
		} catch (Throwable t) {
			return null;
		}
		return Files.isRegularFile(UPDATE_DIR.resolve(staged.file())) ? staged : null;
	}

	private static void markAttempt(Staged staged) {
		try {
			String json = "{\"version\":\"" + staged.version()
					+ "\",\"sha256\":\"" + staged.sha256()
					+ "\",\"file\":\"" + staged.file()
					+ "\",\"attempts\":" + (staged.attempts() + 1) + "}";
			Files.writeString(MARKER, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
		}
	}

	private static String text(String json, String field) {
		Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		return m.find() ? m.group(1) : null;
	}

	private static int number(String json, String field) {
		Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}


	public static Version ownVersion() {
		return self().getMetadata().getVersion();
	}

	public static Path ownJar() {
		try {
			List<Path> paths = self().getOrigin().getPaths();
			if (paths.size() != 1) {
				return null;
			}
			Path p = paths.get(0);
			return (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) ? p : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static ModContainer self() {
		return FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
	}
}
