package com.rufana.instantlore.client.compat;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class XaeroLimboCachePurge {
	private static final Path MARKER = FabricLoader.getInstance().getConfigDir()
			.resolve("instantlore").resolve("xaero-limbo-purged");

	private XaeroLimboCachePurge() {
	}

	public static void run() {
		try {
			if (Files.exists(MARKER)) {
				return;
			}
			Path xaeroDir = FabricLoader.getInstance().getGameDir().resolve("xaero");
			int removed = purge(xaeroDir.resolve("world-map"), XaeroLimboGate.WORLD_MAP_DIR)
					+ purge(xaeroDir.resolve("minimap"), XaeroLimboGate.MINIMAP_DIR);

			Files.createDirectories(MARKER.getParent());
			Files.writeString(MARKER, "удалено папок лимбо: " + removed + "\n");
			if (removed > 0) {
				InstantLore.LOGGER.info("Xaero: removed leaked limbo map cache ({} folders)", removed);
			}
		} catch (Throwable error) {
			InstantLore.LOGGER.warn("Xaero: limbo cache cleanup failed, data untouched", error);
		}
	}

	private static int purge(Path branch, String dirName) throws IOException {
		if (!Files.isDirectory(branch)) {
			return 0;
		}
		List<Path> targets = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(branch)) {
			walk.filter(Files::isDirectory)
					.filter(dir -> dir.getFileName().toString().equals(dirName))
					.forEach(targets::add);
		}
		for (Path dir : targets) {
			deleteRecursively(dir);
		}
		return targets.size();
	}

	private static void deleteRecursively(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			});
		}
	}
}
