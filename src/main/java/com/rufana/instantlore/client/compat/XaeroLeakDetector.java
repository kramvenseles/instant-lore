package com.rufana.instantlore.client.compat;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.LoreReports;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class XaeroLeakDetector {

	private static final long SCAN_PERIOD_SEC = 12;

	private static final int MAX_REPORTS = 8;

	private static final int COUNT_CAP = 500;

	private static final String MOUNT_LOCK_NAME = ".lock";

	private static final Pattern MW_CANONICAL = Pattern.compile("^mw\\$\\d+$");
	private static final Pattern MW_DEFAULT = Pattern.compile("^mw\\$default$");
	private static final Pattern MW_SPAWN = Pattern.compile("^mw-?\\d+,-?\\d+,-?\\d+$");
	private static final Pattern WAYPOINT = Pattern.compile("^(mw.+)_\\d+\\.txt$");

	private static final String[] BRANCHES = {"minimap", "world-map"};
	private static final String SERVER_PREFIX = "Multiplayer_";

	private static final AtomicLong sessionStart = new AtomicLong();
	private static final AtomicInteger reports = new AtomicInteger();
	private static final Set<String> seen = ConcurrentHashMap.newKeySet();
	private static volatile boolean active;
	private static ScheduledExecutorService scanner;

	private static final AtomicInteger limboWritesBlocked = new AtomicInteger();
	private static final AtomicInteger limboSavesBlocked = new AtomicInteger();
	private static final AtomicInteger limboCacheBlocked = new AtomicInteger();
	private static final java.util.concurrent.atomic.AtomicBoolean guardReported =
			new java.util.concurrent.atomic.AtomicBoolean();
	private static final java.util.concurrent.atomic.AtomicBoolean guardBlindReported =
			new java.util.concurrent.atomic.AtomicBoolean();

	private XaeroLeakDetector() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			sessionStart.set(System.currentTimeMillis() - 2_000);
			reports.set(0);
			seen.clear();
			limboWritesBlocked.set(0);
			limboSavesBlocked.set(0);
			limboCacheBlocked.set(0);
			guardReported.set(false);
			guardBlindReported.set(false);
			active = true;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);

		scanner = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "instantlore-xaero-leak-scan");
			t.setDaemon(true);
			return t;
		});
		scanner.scheduleWithFixedDelay(XaeroLeakDetector::scanSafely,
				SCAN_PERIOD_SEC, SCAN_PERIOD_SEC, TimeUnit.SECONDS);
	}

	public static void recordGuardBlock() {
		try {
			limboWritesBlocked.incrementAndGet();
		} catch (Throwable ignored) {
		}
	}

	public static void recordSaveBlock() {
		try {
			limboSavesBlocked.incrementAndGet();
		} catch (Throwable ignored) {
		}
	}

	public static void recordCacheBlock() {
		try {
			limboCacheBlocked.incrementAndGet();
		} catch (Throwable ignored) {
		}
	}

	private static void reportGuardIfNeeded() {
		try {
			int blocked = limboWritesBlocked.get();
			int saveBlocked = limboSavesBlocked.get();
			int cacheBlocked = limboCacheBlocked.get();
			if ((blocked <= 0 && saveBlocked <= 0 && cacheBlocked <= 0)
					|| !guardReported.compareAndSet(false, true)) {
				return;
			}
			JsonObject data = new JsonObject();
			data.addProperty("blocked", blocked);
			data.addProperty("saveBlocked", saveBlocked);
			data.addProperty("cacheBlocked", cacheBlocked);
			data.addProperty("mod", modVersion());
			data.addProperty("xaero", xaeroVersion());
			MinecraftClient.getInstance().execute(() -> LoreReports.send("xaero-guard", data));
			InstantLore.LOGGER.info("Xaero-guard: gate cancelled limbo write (writeMap x{}, save flush x{}, "
					+ "cache x{}) - fix is alive", blocked, saveBlocked, cacheBlocked);
		} catch (Throwable e) {
			InstantLore.LOGGER.debug("Xaero-guard: report not built", e);
		}
	}

	private static void reportGuardBlindIfNeeded() {
		try {
			if (!XaeroLimboGate.isGuardBlind() || !guardBlindReported.compareAndSet(false, true)) {
				return;
			}
			JsonObject data = new JsonObject();
			data.addProperty("reason", String.valueOf(XaeroLimboGate.guardBlindReason()));
			data.addProperty("mod", modVersion());
			data.addProperty("xaero", xaeroVersion());
			MinecraftClient.getInstance().execute(() -> LoreReports.send("xaero-guard-blind", data));
			InstantLore.LOGGER.warn("Xaero-guard: write gate BLIND (destination reflection broke) - "
					+ "limbo write protection disabled, needs re-verification against Xaero {}", xaeroVersion());
		} catch (Throwable e) {
			InstantLore.LOGGER.debug("Xaero-guard-blind: report not built", e);
		}
	}

	private static void scanSafely() {
		if (!active) {
			return;
		}
		reportGuardIfNeeded();
		reportGuardBlindIfNeeded();
		if (reports.get() >= MAX_REPORTS) {
			return;
		}
		try {
			scan();
		} catch (Throwable e) {
			InstantLore.LOGGER.debug("Xaero-leak: scan failed", e);
		}
	}

	private static void scan() throws Exception {
		long since = sessionStart.get();
		Path xaeroDir = FabricLoader.getInstance().getGameDir().resolve("xaero");
		for (String branch : BRANCHES) {
			Path branchDir = xaeroDir.resolve(branch);
			if (!Files.isDirectory(branchDir)) {
				continue;
			}
			for (Path serverDir : childDirs(branchDir)) {
				String serverName = serverDir.getFileName().toString();
				if (!serverName.startsWith(SERVER_PREFIX)) {
					continue;
				}
				String server = serverName.substring(SERVER_PREFIX.length());
				for (Path dimDir : childDirs(serverDir)) {
					scanDimension(branch, server, dimDir, since);
				}
			}
		}
	}

	private static void scanDimension(String branch, String server, Path dimDir, long since)
			throws Exception {
		String dim = dimDir.getFileName().toString();
		boolean limbo = dim.equals(XaeroLimboGate.WORLD_MAP_DIR)
				|| dim.equals(XaeroLimboGate.MINIMAP_DIR);

		for (Path bucket : childDirs(dimDir)) {
			String name = bucket.getFileName().toString();
			if (!name.startsWith("mw")) {
				continue;
			}
			String cls = limbo ? "LIMBO" : classify(name);
			if (cls == null) {
				continue;
			}
			int tiles = countRecent(bucket, since);
			if (tiles > 0) {
				report(branch, server, dim, name, cls, tiles, since,
						firstRecentName(bucket, since), recentExts(bucket, since));
			}
		}

		if (!limbo) {
			for (Path file : childFiles(dimDir)) {
				var m = WAYPOINT.matcher(file.getFileName().toString());
				if (!m.matches()) {
					continue;
				}
				String cls = classify(m.group(1));
				if (cls != null && recent(file, since)) {
					String fn = file.getFileName().toString();
					report(branch, server, dim, fn, cls, 1, since, fn, extLabel(fn));
				}
			}
		}
	}

	private static String classify(String bucket) {
		if (MW_CANONICAL.matcher(bucket).matches()) {
			return null;
		}
		if (MW_DEFAULT.matcher(bucket).matches()) {
			return "DEFAULT";
		}
		if (MW_SPAWN.matcher(bucket).matches()) {
			return "SPAWN";
		}
		return "???";
	}

	private static boolean isLeakFile(Path p, long since) {
		return Files.isRegularFile(p)
				&& !p.getFileName().toString().equals(MOUNT_LOCK_NAME)
				&& recent(p, since);
	}

	private static int countRecent(Path root, long since) throws Exception {
		try (Stream<Path> walk = Files.walk(root)) {
			return (int) walk.filter(p -> isLeakFile(p, since))
					.limit(COUNT_CAP)
					.count();
		}
	}

	private static String firstRecentName(Path root, long since) {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(p -> isLeakFile(p, since))
					.findFirst()
					.map(p -> root.relativize(p).toString())
					.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	private static String recentExts(Path root, long since) {
		try (Stream<Path> walk = Files.walk(root)) {
			java.util.TreeSet<String> exts = new java.util.TreeSet<>();
			walk.filter(p -> isLeakFile(p, since))
					.limit(COUNT_CAP)
					.forEach(p -> exts.add(extLabel(p.getFileName().toString())));
			return exts.isEmpty() ? null : String.join(",", exts);
		} catch (Exception e) {
			return null;
		}
	}

	private static String extLabel(String name) {
		if (name.endsWith(".xwmc.outdated")) {
			return "xwmc.outdated";
		}
		if (name.endsWith(".xwmc")) {
			return "xwmc";
		}
		int dot = name.lastIndexOf('.');
		return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "(no-ext)";
	}

	private static boolean recent(Path p, long since) {
		try {
			return Files.getLastModifiedTime(p).toMillis() >= since;
		} catch (Exception e) {
			return false;
		}
	}

	private static void report(String branch, String server, String dim, String bucket,
			String cls, int tiles, long since, String sample, String exts) {
		String key = branch + "|" + server + "|" + dim + "|" + cls;
		if (!seen.add(key)) {
			return;
		}
		if (reports.incrementAndGet() > MAX_REPORTS) {
			return;
		}
		long sec = Math.max(0, (System.currentTimeMillis() - since - 2_000) / 1000);

		JsonObject data = new JsonObject();
		data.addProperty("branch", branch);
		data.addProperty("server", server);
		data.addProperty("dim", dim);
		data.addProperty("bucket", bucket);
		data.addProperty("class", cls);
		data.addProperty("tiles", tiles);
		data.addProperty("sec", sec);
		data.addProperty("mod", modVersion());
		data.addProperty("xaero", xaeroVersion());
		data.addProperty("guardBlocked", limboWritesBlocked.get());
		data.addProperty("saveBlocked", limboSavesBlocked.get());
		data.addProperty("cacheBlocked", limboCacheBlocked.get());
		data.addProperty("guardBlind", XaeroLimboGate.isGuardBlind());
		if (sample != null) {
			data.addProperty("sample", sample);
		}
		if (exts != null) {
			data.addProperty("exts", exts);
		}

		MinecraftClient.getInstance().execute(() -> LoreReports.send("xaero-leak", data));
		InstantLore.LOGGER.info("Xaero-leak: {} {}/{} bucket={} ({}), tiles~{}, +{}s after join",
				cls, branch, dim, bucket, server, tiles, sec);
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(InstantLore.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
	}

	private static String xaeroVersion() {
		return FabricLoader.getInstance().getModContainer("xaeroworldmap")
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("нет");
	}

	private static java.util.List<Path> childDirs(Path dir) throws Exception {
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(Files::isDirectory).sorted(Comparator.naturalOrder()).toList();
		}
	}

	private static java.util.List<Path> childFiles(Path dir) throws Exception {
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(Files::isRegularFile).toList();
		}
	}
}
