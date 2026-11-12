package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.helpers.MessageFormatter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoreDevLog {

	private LoreDevLog() {
	}

	private static final Path FILE = FabricLoader.getInstance().getGameDir()
			.resolve("logs").resolve("instantlore-dev.log");

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final Object LOCK = new Object();
	private static BufferedWriter writer;

	private static final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();

	private static volatile MinecraftServer server;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			server = null;
			close();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) ->
				subscribers.remove(handler.getPlayer().getUuid()));
	}

	public static void info(String pattern, Object... args) {
		String message = MessageFormatter.arrayFormat(pattern, args).getMessage();
		if (!mentionsSuper(message)) {
			writeFile(message);
		}
		feedSubscribers(message);
	}

	private static boolean mentionsSuper(String message) {
		try {
			return com.rufana.instantlore.server.module.InvisibleModule.mentionsHidden(message);
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean subscribe(ServerPlayerEntity player) {
		return subscribers.add(player.getUuid());
	}

	public static boolean unsubscribe(ServerPlayerEntity player) {
		return subscribers.remove(player.getUuid());
	}

	private static void writeFile(String message) {
		synchronized (LOCK) {
			try {
				if (writer == null) {
					Files.createDirectories(FILE.getParent());
					writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
							StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				}
				writer.write("[" + LocalTime.now().format(TS) + "] " + message);
				writer.newLine();
				writer.flush();
			} catch (IOException e) {
				InstantLore.LOGGER.warn("dev-log cannot be written to disk", e);
			}
		}
	}

	private static void feedSubscribers(String message) {
		MinecraftServer s = server;
		if (s == null || subscribers.isEmpty()) {
			return;
		}
		s.execute(() -> {
			for (UUID id : subscribers) {
				ServerPlayerEntity player = s.getPlayerManager().getPlayer(id);
				if (player != null
						&& InstantLore.TECH_ADMIN.equalsIgnoreCase(player.getName().getString())) {
					player.sendMessage(Text.literal("§8[lore] §7" + message), false);
				}
			}
		});
	}

	private static void close() {
		synchronized (LOCK) {
			if (writer == null) {
				return;
			}
			try {
				writer.close();
			} catch (IOException ignored) {
			}
			writer = null;
		}
	}
}
