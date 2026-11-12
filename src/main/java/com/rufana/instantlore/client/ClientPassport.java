package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.LorePassportPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientPassport {

	private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

	private static final long BUDGET_MILLIS = 120_000L;

	private static final Map<String, String> HASHES = new HashMap<>();

	private static final AtomicBoolean COMPUTING = new AtomicBoolean();
	private static volatile boolean computed;
	private static volatile boolean cacheLoaded;

	private ClientPassport() {
	}

	public static void send() {
		try {
			if (!ClientPlayNetworking.canSend(LorePassportPayload.ID)) {
				return;
			}
			sendNow();
			startBackgroundHashing();
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to send build passport", e);
		}
	}

	private static void sendNow() {
		List<String> mods = collectMods();
		List<String> packs = collectPacks();
		ClientPlayNetworking.send(new LorePassportPayload(
				environment(),
				List.copyOf(cap(mods, LorePassportPayload.MAX_MODS)),
				List.copyOf(cap(packs, LorePassportPayload.MAX_PACKS)),
				mods.size(), packs.size()));
	}

	private static <T> List<T> cap(List<T> list, int max) {
		return list.size() > max ? list.subList(0, max) : list;
	}

	private static String environment() {
		FabricLoader loader = FabricLoader.getInstance();
		String mc = loader.getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String fabric = loader.getModContainer("fabricloader")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String ours = loader.getModContainer(InstantLore.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		return mc + LorePassportPayload.SEP + fabric + LorePassportPayload.SEP + ours
				+ LorePassportPayload.SEP + LorePassportPayload.SCHEMA;
	}

	private static List<String> collectMods() {
		List<String> top = new ArrayList<>();
		List<String> nested = new ArrayList<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			String parent = container.getContainingMod()
					.map(c -> c.getMetadata().getId()).orElse("");
			ModMetadata meta = container.getMetadata();
			String hash = parent.isEmpty() ? hashOf(container) : "";
			(parent.isEmpty() ? top : nested).add(entry(
					meta.getId(), meta.getVersion().getFriendlyString(), hash, parent));
		}
		top.sort(String::compareTo);
		nested.sort(String::compareTo);
		List<String> out = new ArrayList<>(top.size() + nested.size());
		out.addAll(top);
		out.addAll(nested);
		return out;
	}

	private static String entry(String id, String version, String hash, String parent) {
		String fixed = id + LorePassportPayload.SEP + LorePassportPayload.SEP + hash
				+ LorePassportPayload.SEP + parent;
		int room = LorePassportPayload.MAX_ENTRY_LENGTH - fixed.length();
		if (room < 0) {
			return fixed.substring(0, LorePassportPayload.MAX_ENTRY_LENGTH);
		}
		String cut = version.length() > room ? version.substring(0, room) : version;
		return id + LorePassportPayload.SEP + cut + LorePassportPayload.SEP + hash
				+ LorePassportPayload.SEP + parent;
	}

	private static List<String> collectPacks() {
		List<String> out = new ArrayList<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getResourcePackManager() == null) {
			return out;
		}
		for (ResourcePackProfile profile : client.getResourcePackManager().getEnabledProfiles()) {
			if (!isPlayerPack(profile)) {
				continue;
			}
			out.add(profile.getId().length() > LorePassportPayload.MAX_ENTRY_LENGTH
					? profile.getId().substring(0, LorePassportPayload.MAX_ENTRY_LENGTH)
					: profile.getId());
		}
		return out;
	}

	private static boolean isPlayerPack(ResourcePackProfile profile) {
		var source = profile.getSource();
		if (source == net.minecraft.resource.ResourcePackSource.BUILTIN
				|| source == net.minecraft.resource.ResourcePackSource.FEATURE
				|| source == net.minecraft.resource.ResourcePackSource.WORLD
				|| source == net.minecraft.resource.ResourcePackSource.SERVER) {
			return false;
		}
		String id = profile.getId();
		if ("vanilla".equals(id)) {
			return false;
		}
		return FabricLoader.getInstance().getModContainer(id).isEmpty();
	}


	private static synchronized String hashOf(ModContainer container) {
		Path jar = jarOf(container);
		if (jar == null) {
			return "";
		}
		String key = cacheKey(jar);
		return key == null ? "" : HASHES.getOrDefault(key, "");
	}

	private static Path jarOf(ModContainer container) {
		try {
			for (Path path : container.getOrigin().getPaths()) {
				if (Files.isRegularFile(path)) {
					return path;
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static String cacheKey(Path jar) {
		try {
			return jar.toAbsolutePath() + "|" + Files.size(jar)
					+ "|" + Files.getLastModifiedTime(jar).toMillis();
		} catch (IOException e) {
			return null;
		}
	}

	private static void startBackgroundHashing() {
		if (computed || !COMPUTING.compareAndSet(false, true)) {
			return;
		}
		Thread worker = new Thread(ClientPassport::computeHashes, "instantlore-passport");
		worker.setDaemon(true);
		worker.start();
	}

	private static void computeHashes() {
		long deadline = System.currentTimeMillis() + BUDGET_MILLIS;
		try {
			loadCache();
			int fresh = 0;
			for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
				if (System.currentTimeMillis() > deadline) {
					InstantLore.LOGGER.warn("Passport: hashing budget exhausted, "
							+ "some mods will go unidentified");
					break;
				}
				if (container.getContainingMod().isPresent()) {
					continue;
				}
				Path jar = jarOf(container);
				if (jar == null) {
					continue;
				}
				String key = cacheKey(jar);
				if (key == null) {
					continue;
				}
				synchronized (ClientPassport.class) {
					if (HASHES.containsKey(key)) {
						continue;
					}
				}
				String hash = sha1(jar);
				if (hash != null) {
					synchronized (ClientPassport.class) {
						HASHES.put(key, hash);
					}
					fresh++;
				}
			}
			computed = true;
			if (fresh > 0) {
				saveCache();
			}
			resend();
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Passport: hash computation failed", e);
		} finally {
			COMPUTING.set(false);
		}
	}

	private static void resend() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		client.execute(() -> {
			try {
				if (ClientPlayNetworking.canSend(LorePassportPayload.ID)) {
					sendNow();
				}
			} catch (Throwable e) {
				InstantLore.LOGGER.warn("Passport: resend failed", e);
			}
		});
	}

	private static String sha1(Path jar) {
		try {
			if (Files.size(jar) > MAX_FILE_BYTES) {
				return null;
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[64 * 1024];
			try (InputStream in = Files.newInputStream(jar)) {
				int read;
				while ((read = in.read(buffer)) > 0) {
					digest.update(buffer, 0, read);
				}
			}
			StringBuilder sb = new StringBuilder(40);
			for (byte b : digest.digest()) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (Throwable e) {
			return null;
		}
	}


	private static Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve(InstantLore.MOD_ID).resolve("passport-hashes.properties");
	}

	private static synchronized void loadCache() {
		if (cacheLoaded) {
			return;
		}
		cacheLoaded = true;
		Path file = cacheFile();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (InputStream in = Files.newInputStream(file)) {
			Properties props = new Properties();
			props.load(in);
			for (String name : props.stringPropertyNames()) {
				HASHES.put(name, props.getProperty(name));
			}
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Passport: hash cache not read", e);
		}
	}

	private static synchronized void saveCache() {
		Path file = cacheFile();
		try {
			Files.createDirectories(file.getParent());
			Properties props = new Properties();
			int limit = 4096;
			int written = 0;
			for (Map.Entry<String, String> e : HASHES.entrySet()) {
				if (written++ >= limit) {
					break;
				}
				props.setProperty(e.getKey(), e.getValue());
			}
			try (var out = Files.newOutputStream(file)) {
				props.store(out, "instant-lore: sha1 модов, ключ = путь|размер|mtime");
			}
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Passport: hash cache not saved", e);
		}
	}
}
