package com.rufana.instantlore.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.ContentSource;
import com.rufana.instantlore.update.SwapLauncher;
import com.rufana.instantlore.update.UpdateLog;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompanionUpdater {

	private static final Path LOCAL_REGISTRY = SwapLauncher.UPDATE_DIR.resolve("companions-local.json");

	private static final ExecutorService WORKER =
			Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "instantlore-companions");
				thread.setDaemon(true);
				return thread;
			});

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private CompanionUpdater() {
	}

	public static void checkAndDeliver() {
		Path ownJar = SwapLauncher.ownJar();
		if (ownJar == null) {
			UpdateLog.line("companion: пропуск — мод запущен не из jar в mods/");
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			return;
		}

		WORKER.execute(() -> {
			try {
				deliver(ownJar.getParent());
			} catch (Throwable t) {
				UpdateLog.line("companion: проверка не удалась — " + t);
				InstantLore.LOGGER.warn("Companion mod delivery: check failed", t);
			} finally {
				RUNNING.set(false);
			}
		});
	}

	private static void deliver(Path modsDir) {
		String json = ContentSource.fetchCompanions();
		if (json == null) {
			return;
		}

		List<Wanted> wanted;
		try {
			wanted = parse(json);
		} catch (Throwable t) {
			UpdateLog.line("companion: манифест не разобрался — " + t);
			return;
		}

		Map<String, String> registry = readRegistry();

		for (Wanted mod : wanted) {
			try {
				deliverOne(modsDir, mod, registry);
			} catch (Throwable t) {
				UpdateLog.line("companion: " + mod.id + " не доставлен — " + t);
				InstantLore.LOGGER.warn("Delivery of companion {} failed", mod.id, t);
			}
		}

		for (Map.Entry<String, String> old : registry.entrySet()) {
			boolean stillWanted = wanted.stream().anyMatch(w -> w.id.equals(old.getKey()));
			if (!stillWanted) {
				UpdateLog.line("companion: " + old.getKey() + " (" + old.getValue()
						+ ") убран из манифеста — снять вручную (авто-удаление — отдельный шаг)");
			}
		}
	}

	private static void deliverOne(Path modsDir, Wanted mod, Map<String, String> registry)
			throws Exception {
		Path dest = modsDir.resolve(mod.file);

		if (FabricLoader.getInstance().isModLoaded(mod.id)) {
			return;
		}

		if (Files.isRegularFile(dest)
				&& mod.sha256.equalsIgnoreCase(ContentSource.sha256(Files.readAllBytes(dest)))) {
			rememberDelivered(registry, mod);
			return;
		}

		byte[] jar = ContentSource.fetchAsset(mod.path, mod.sha256, mod.size);
		Files.createDirectories(modsDir);
		Path part = modsDir.resolve(mod.file + ".part");
		Files.write(part, jar);
		Files.move(part, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

		rememberDelivered(registry, mod);
		UpdateLog.line("companion: " + mod.id + " (" + mod.file
				+ ") доставлен, применится при следующем перезапуске игры");
		InstantLore.LOGGER.info("Companion {} delivered to mods/ - takes effect on game restart",
				mod.id);
	}


	private record Wanted(String id, String file, String path, String sha256, long size) {
	}

	private static List<Wanted> parse(String json) {
		JsonObject doc = JsonParser.parseString(json).getAsJsonObject();
		JsonArray mods = doc.getAsJsonArray("mods");
		List<Wanted> out = new ArrayList<>();
		if (mods == null) {
			return out;
		}
		for (JsonElement el : mods) {
			JsonObject o = el.getAsJsonObject();
			String id = o.get("id").getAsString();
			String file = o.get("file").getAsString();
			String path = o.get("path").getAsString();
			String sha = o.get("sha256").getAsString();
			long size = o.get("size").getAsLong();

			if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.contains("..")
					|| !file.endsWith(".jar")) {
				UpdateLog.line("companion: пропуск записи с недопустимым именем файла '" + file + "'");
				continue;
			}
			out.add(new Wanted(id, file, path, sha, size));
		}
		return out;
	}


	private static Map<String, String> readRegistry() {
		Map<String, String> map = new LinkedHashMap<>();
		try {
			if (!Files.isRegularFile(LOCAL_REGISTRY)) {
				return map;
			}
			JsonArray arr = JsonParser.parseString(Files.readString(LOCAL_REGISTRY)).getAsJsonArray();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				map.put(o.get("id").getAsString(), o.get("file").getAsString());
			}
		} catch (Throwable t) {
			InstantLore.LOGGER.debug("Delivered companion registry not read - treating as empty", t);
		}
		return map;
	}

	private static void rememberDelivered(Map<String, String> registry, Wanted mod) {
		if (mod.file.equals(registry.get(mod.id))) {
			return;
		}
		registry.put(mod.id, mod.file);
		writeRegistry(registry);
	}

	private static void writeRegistry(Map<String, String> registry) {
		try {
			JsonArray arr = new JsonArray();
			for (Map.Entry<String, String> e : registry.entrySet()) {
				JsonObject o = new JsonObject();
				o.addProperty("id", e.getKey());
				o.addProperty("file", e.getValue());
				arr.add(o);
			}
			Files.createDirectories(LOCAL_REGISTRY.getParent());
			Files.writeString(LOCAL_REGISTRY, arr.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			InstantLore.LOGGER.debug("Delivered companion registry not written", t);
		}
	}
}
