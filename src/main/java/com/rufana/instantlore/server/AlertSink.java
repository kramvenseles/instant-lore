package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;

import net.fabricmc.loader.api.FabricLoader;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class AlertSink {

	private static final String CONFIG_NAME = "alerts.properties";

	private static final int MAX_QUEUED = 64;

	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 10_000;

	private static volatile String url;
	private static volatile String token;
	private static volatile String source = "instantlore";
	private static volatile boolean loaded;

	private static final AtomicInteger queued = new AtomicInteger();
	private static ExecutorService sender;

	private AlertSink() {
	}

	public static synchronized void load() {
		loaded = true;
		try {
			Path file = FabricLoader.getInstance().getConfigDir()
					.resolve(InstantLore.MOD_ID).resolve(CONFIG_NAME);
			if (!Files.isRegularFile(file)) {
				return;
			}
			Properties props = new Properties();
			try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				props.load(in);
			}
			String u = props.getProperty("url", "").trim();
			String t = props.getProperty("token", "").trim();
			if (u.isEmpty() || t.isEmpty()) {
				InstantLore.LOGGER.warn("[alerts] {} exists but url or token is empty - channel is silent",
						CONFIG_NAME);
				return;
			}
			url = u;
			token = t;
			source = props.getProperty("source", "instantlore").trim();
			sender = Executors.newSingleThreadExecutor(r -> {
				Thread thread = new Thread(r, "instantlore-alerts");
				thread.setDaemon(true);
				return thread;
			});
			InstantLore.LOGGER.info("[alerts] signal channel enabled: {} (source={})", url, source);
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("[alerts] settings not read - channel is silent", e);
		}
	}

	public static boolean enabled() {
		return url != null && token != null && sender != null;
	}

	public static void send(String level, String kind, String player, String text, String details) {
		if (!loaded) {
			load();
		}
		if (!enabled()) {
			return;
		}
		if (queued.get() >= MAX_QUEUED) {
			return;
		}
		queued.incrementAndGet();
		try {
			sender.execute(() -> {
				try {
					post(json(level, kind, player, text, details));
				} catch (Throwable e) {
					InstantLore.LOGGER.warn("[alerts] signal not sent: {}", e.toString());
				} finally {
					queued.decrementAndGet();
				}
			});
		} catch (Throwable e) {
			queued.decrementAndGet();
		}
	}

	private static String json(String level, String kind, String player, String text,
			String details) {
		StringBuilder sb = new StringBuilder(256);
		sb.append('{');
		field(sb, "source", source).append(',');
		field(sb, "level", level == null ? "yellow" : level).append(',');
		field(sb, "kind", kind == null ? "doctor" : kind).append(',');
		field(sb, "player", player == null ? "" : player).append(',');
		field(sb, "text", text == null ? "" : text).append(',');
		field(sb, "details", details == null ? "" : details);
		return sb.append('}').toString();
	}

	private static StringBuilder field(StringBuilder sb, String name, String value) {
		return sb.append('"').append(name).append("\":\"").append(escape(value)).append('"');
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}

	private static void post(String body) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		conn.setRequestMethod("POST");
		conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
		conn.setReadTimeout(READ_TIMEOUT_MS);
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		conn.setRequestProperty("X-Alert-Token", token);
		try (OutputStream out = conn.getOutputStream()) {
			out.write(body.getBytes(StandardCharsets.UTF_8));
		}
		int code = conn.getResponseCode();
		if (code / 100 != 2) {
			InstantLore.LOGGER.warn("[alerts] receiver responded {}", code);
		}
		conn.disconnect();
	}
}
