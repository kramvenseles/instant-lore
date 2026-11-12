package com.rufana.instantlore.client.compat;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class XaeroMigration {

	private static final String[] BRANCHES = {"minimap", "world-map"};

	private static final Pattern SERVER_BASED = Pattern.compile("^mw\\$\\d+$");

	private static final Pattern CLIENT_BASED =
			Pattern.compile("^mw\\$default$|^mw-?\\d+,-?\\d+,-?\\d+$");

	private static final Pattern WAYPOINT_FILE = Pattern.compile("^(.+)_(\\d+)\\.txt$");

	private static final String LOCK_FILE = ".lock";

	private static final Path MARKER = FabricLoader.getInstance().getConfigDir()
			.resolve("instantlore").resolve("xaero-migrated");

	private XaeroMigration() {
	}

	public static void run() {
		try {
			if (Files.exists(MARKER)) {
				return;
			}
			int moved = 0;
			Path xaeroDir = FabricLoader.getInstance().getGameDir().resolve("xaero");
			for (String branch : BRANCHES) {
				Path branchDir = xaeroDir.resolve(branch);
				if (!Files.isDirectory(branchDir)) {
					continue;
				}
				for (Path serverDir : childDirs(branchDir)) {
					for (Path dimensionDir : childDirs(serverDir)) {
						moved += migrateTiles(dimensionDir) + migrateWaypoints(dimensionDir);
					}
				}
			}
			if (moved > 0) {
				Files.createDirectories(MARKER.getParent());
				Files.writeString(MARKER, "перенесено файлов: " + moved + "\n");
				InstantLore.LOGGER.info("Xaero: migrated {} map and waypoint files to the new multiworld", moved);
			}
		} catch (Throwable error) {
			InstantLore.LOGGER.warn("Xaero: map migration failed, data untouched", error);
		}
	}

	private static int migrateTiles(Path dimensionDir) throws IOException {
		List<Path> old = new ArrayList<>();
		List<Path> fresh = new ArrayList<>();
		for (Path dir : childDirs(dimensionDir)) {
			String name = dir.getFileName().toString();
			if (CLIENT_BASED.matcher(name).matches()) {
				old.add(dir);
			} else if (SERVER_BASED.matcher(name).matches()) {
				fresh.add(dir);
			}
		}
		if (old.size() != 1 || fresh.size() != 1) {
			return 0;
		}
		return copyMissing(old.get(0), fresh.get(0));
	}

	private static int migrateWaypoints(Path dimensionDir) throws IOException {
		List<Path> old = new ArrayList<>();
		String freshId = null;
		boolean ambiguous = false;
		for (Path file : childFiles(dimensionDir)) {
			Matcher matcher = WAYPOINT_FILE.matcher(file.getFileName().toString());
			if (!matcher.matches()) {
				continue;
			}
			String worldId = matcher.group(1);
			if (CLIENT_BASED.matcher(worldId).matches()) {
				old.add(file);
			} else if (SERVER_BASED.matcher(worldId).matches()) {
				if (freshId != null && !freshId.equals(worldId)) {
					ambiguous = true;
				}
				freshId = worldId;
			}
		}
		if (freshId == null || ambiguous || old.isEmpty()) {
			return 0;
		}
		int copied = 0;
		for (Path source : old) {
			Matcher matcher = WAYPOINT_FILE.matcher(source.getFileName().toString());
			if (!matcher.matches()) {
				continue;
			}
			Path target = dimensionDir.resolve(freshId + "_" + matcher.group(2) + ".txt");
			copied += appendMissingLines(source, target);
		}
		return copied;
	}

	private static int appendMissingLines(Path source, Path target) throws IOException {
		if (!Files.isRegularFile(target)) {
			Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
			return 1;
		}
		List<String> present = Files.readAllLines(target);
		List<String> missing = new ArrayList<>();
		for (String line : Files.readAllLines(source)) {
			String trimmed = line.strip();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !present.contains(line)) {
				missing.add(line);
			}
		}
		if (missing.isEmpty()) {
			return 0;
		}
		List<String> merged = new ArrayList<>(present);
		merged.addAll(missing);
		Files.write(target, merged);
		return missing.size();
	}

	private static int copyMissing(Path from, Path to) throws IOException {
		int copied = 0;
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path source : walk.toList()) {
				if (source.getFileName().toString().equals(LOCK_FILE)) {
					continue;
				}
				Path target = to.resolve(from.relativize(source).toString());
				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else if (!Files.exists(target)) {
					Files.createDirectories(target.getParent());
					Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
					copied++;
				}
			}
		}
		return copied;
	}

	private static List<Path> childDirs(Path dir) throws IOException {
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(Files::isDirectory).toList();
		}
	}

	private static List<Path> childFiles(Path dir) throws IOException {
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(Files::isRegularFile).toList();
		}
	}
}
