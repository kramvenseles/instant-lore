package com.rufana.instantlore.server.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreDevLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleRegistry {

	private ModuleRegistry() {
	}

	private static final Map<String, LoreModule> MODULES = new LinkedHashMap<>();

	private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();

	private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

	private static final Path OVERRIDES_FILE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("module-overrides.json");

	public static void register(LoreModule module) {
		MODULES.put(module.id(), module);
	}

	public static boolean has(String id) {
		return MODULES.containsKey(id);
	}

	public static java.util.Collection<LoreModule> all() {
		return MODULES.values();
	}

	public static boolean isActive(String id) {
		return ACTIVE.contains(id);
	}

	public static String enabledSource(String id) {
		if (OVERRIDES.containsKey(id)) {
			return "оверрайд";
		}
		return "раздача/дефолт";
	}

	public static void init() {
		loadOverrides();
		ServerTickEvents.END_SERVER_TICK.register(ModuleRegistry::tickAll);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (String id : ACTIVE) {
				safe(id, () -> MODULES.get(id).onDisable(server));
			}
			ACTIVE.clear();
		});
	}

	private static void tickAll(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (String id : ACTIVE.toArray(new String[0])) {
			LoreModule module = MODULES.get(id);
			if (module == null) {
				continue;
			}
			try {
				module.tick(server);
			} catch (Throwable t) {
				ACTIVE.remove(id);
				InstantLore.LOGGER.error(
						"Module '{}' failed in tick - removed to avoid spam. Re-enable: "
								+ "/lore module on {}", id, id, t);
			}
		}
	}

	public static void applyConfig(MinecraftServer server, String modulesJson) {
		JsonObject modules = parseModules(modulesJson);

		for (LoreModule module : MODULES.values()) {
			JsonObject entry = modules != null && modules.has(module.id())
					&& modules.get(module.id()).isJsonObject()
					? modules.getAsJsonObject(module.id()) : null;

			JsonObject config = entry != null && entry.has("config") && entry.get("config").isJsonObject()
					? entry.getAsJsonObject("config") : new JsonObject();

			safe(module.id(), () -> module.configure(config));

			boolean desired = resolveEnabled(module, entry);
			transition(server, module, desired);
		}
	}

	private static boolean resolveEnabled(LoreModule module, JsonObject entry) {
		Boolean override = OVERRIDES.get(module.id());
		if (override != null) {
			return override;
		}
		if (entry != null && entry.has("enabled") && entry.get("enabled").isJsonPrimitive()) {
			return entry.get("enabled").getAsBoolean();
		}
		return module.defaultEnabled();
	}

	public static boolean setEnabled(MinecraftServer server, String id, boolean on) {
		LoreModule module = MODULES.get(id);
		if (module == null) {
			return false;
		}
		OVERRIDES.put(id, on);
		saveOverrides();
		transition(server, module, on);
		return true;
	}

	private static void transition(MinecraftServer server, LoreModule module, boolean desired) {
		boolean was = ACTIVE.contains(module.id());
		if (desired && !was) {
			ACTIVE.add(module.id());
			safe(module.id(), () -> module.onEnable(server));
		} else if (!desired && was) {
			ACTIVE.remove(module.id());
			safe(module.id(), () -> module.onDisable(server));
		}
	}

	private static JsonObject parseModules(String modulesJson) {
		if (modulesJson == null) {
			return null;
		}
		try {
			JsonElement root = JsonParser.parseString(modulesJson);
			if (root.isJsonObject() && root.getAsJsonObject().has("modules")
					&& root.getAsJsonObject().get("modules").isJsonObject()) {
				return root.getAsJsonObject().getAsJsonObject("modules");
			}
			InstantLore.LOGGER.warn("modules.json has no 'modules' object - using defaults");
		} catch (RuntimeException e) {
			InstantLore.LOGGER.warn("modules.json failed to parse - using defaults", e);
		}
		return null;
	}

	private static void safe(String id, ThrowingRunnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			InstantLore.LOGGER.error("Module '{}': lifecycle step failed", id, t);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Throwable;
	}


	private static void loadOverrides() {
		if (!Files.exists(OVERRIDES_FILE)) {
			return;
		}
		try {
			String json = Files.readString(OVERRIDES_FILE, StandardCharsets.UTF_8);
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : object.entrySet()) {
				OVERRIDES.put(e.getKey(), e.getValue().getAsBoolean());
			}
			LoreDevLog.info("Module overrides loaded: {}", OVERRIDES.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("module-overrides.json not read: {}", e.toString());
		}
	}

	private static void saveOverrides() {
		try {
			Files.createDirectories(OVERRIDES_FILE.getParent());
			JsonObject object = new JsonObject();
			for (Map.Entry<String, Boolean> e : OVERRIDES.entrySet()) {
				object.addProperty(e.getKey(), e.getValue());
			}
			Path temp = OVERRIDES_FILE.resolveSibling(OVERRIDES_FILE.getFileName() + ".tmp");
			Files.writeString(temp, object.toString(), StandardCharsets.UTF_8);
			Files.move(temp, OVERRIDES_FILE, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("module-overrides.json not saved: {}", e.toString());
		}
	}
}
