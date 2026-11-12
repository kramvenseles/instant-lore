package com.rufana.instantlore.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rufana.instantlore.InstantLore;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WhitelistManager {

	private WhitelistManager() {}

	private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "instantlore-whitelist");
		t.setDaemon(true);
		return t;
	});


	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("whitelist")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
				.then(CommandManager.literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
				.then(CommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())))
				.then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.suggests((ctx, b) -> CommandSource.suggestMatching(onlineNotWhitelisted(ctx.getSource()), b))
								.executes(ctx -> add(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.suggests((ctx, b) -> CommandSource.suggestMatching(whitelistedNames(ctx.getSource()), b))
								.executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
	}

	// --- add ----------------------------------------------------------------

	private static int add(ServerCommandSource source, String name) {
		MinecraftServer server = source.getServer();
		Whitelist wl = server.getPlayerManager().getWhitelist();

		PlayerConfigEntry offline = PlayerConfigEntry.fromNickname(name);
		boolean addedOffline = false;
		if (!wl.isAllowed(offline)) {
			wl.add(new WhitelistEntry(offline));
			addedOffline = true;
		}
		save(wl);
		final boolean ao = addedOffline;
		source.sendFeedback(() -> Text.literal(ao
				? "Вайтлист: добавлен " + name + " (offline). Проверяю Mojang…"
				: "Вайтлист: " + name + " уже был (offline). Проверяю Mojang…"), true);

		LOOKUP.execute(() -> {
			NameId res;
			try {
				res = mojangLookup(name);
			} catch (Throwable t) {
				InstantLore.LOGGER.warn("whitelist: Mojang request for {} failed: {}", name, t.toString());
				feedback(server, source, "Whitelist: Mojang unavailable for " + name
						+ " — only the offline entry was kept, retry for premium.");
				return;
			}
			if (res == null) {
				feedback(server, source, "Вайтлист: " + name
						+ " — офиц. аккаунта нет (cracked), offline-записи достаточно.");
				return;
			}
			server.execute(() -> {
				try {
					PlayerConfigEntry online = new PlayerConfigEntry(res.id, res.name);
					boolean added = false;
					if (!wl.isAllowed(online)) {
						wl.add(new WhitelistEntry(online));
						added = true;
					}
					save(wl);
					final boolean addedOnline = added;
					source.sendFeedback(() -> Text.literal(addedOnline
							? "Вайтлист: " + name + " — premium, добавлена онлайн-запись " + res.name + "."
							: "Вайтлист: " + name + " — premium уже был в списке."), true);
				} catch (Throwable t) {
					InstantLore.LOGGER.warn("whitelist: failed to append premium entry {}: {}", name, t.toString());
				}
			});
		});
		return 1;
	}

	// --- remove -------------------------------------------------------------

	private static int remove(ServerCommandSource source, String name) {
		MinecraftServer server = source.getServer();
		Whitelist wl = server.getPlayerManager().getWhitelist();

		List<PlayerConfigEntry> toRemove = new ArrayList<>();
		for (WhitelistEntry e : wl.values()) {
			PlayerConfigEntry k = e.getKey();
			if (k.name().equalsIgnoreCase(name)) {
				toRemove.add(k);
			}
		}
		PlayerConfigEntry offline = PlayerConfigEntry.fromNickname(name);
		if (wl.isAllowed(offline) && toRemove.stream().noneMatch(k -> k.id().equals(offline.id()))) {
			toRemove.add(offline);
		}

		int n = 0;
		for (PlayerConfigEntry k : toRemove) {
			if (wl.remove(k)) {
				n++;
			}
		}
		save(wl);
		final int cnt = n;
		source.sendFeedback(() -> Text.literal(cnt > 0
				? "Вайтлист: удалён " + name + " (" + cnt + " " + records(cnt) + ")."
				: "Вайтлист: " + name + " в списке не найден."), true);
		return cnt;
	}

	// --- list ---------------------------------------------------------------

	private static int list(ServerCommandSource source) {
		Whitelist wl = source.getServer().getPlayerManager().getWhitelist();
		Map<String, List<PlayerConfigEntry>> byName = new TreeMap<>();
		for (WhitelistEntry e : wl.values()) {
			PlayerConfigEntry k = e.getKey();
			byName.computeIfAbsent(k.name().toLowerCase(Locale.ROOT), x -> new ArrayList<>()).add(k);
		}
		if (byName.isEmpty()) {
			source.sendFeedback(() -> Text.literal("Вайтлист пуст."), false);
			return 0;
		}
		StringBuilder sb = new StringBuilder("Вайтлист (" + byName.size() + "):");
		for (List<PlayerConfigEntry> group : byName.values()) {
			String display = group.get(0).name();
			UUID off = PlayerConfigEntry.fromNickname(display).id();
			boolean premium = group.stream().anyMatch(k -> !k.id().equals(off));
			sb.append("\n  ").append(display).append(premium ? " [premium]" : " [cracked]");
		}
		String out = sb.toString();
		source.sendFeedback(() -> Text.literal(out), false);
		return byName.size();
	}

	// --- on/off/reload ------------------------------------------------------

	private static int setEnabled(ServerCommandSource source, boolean on) {
		MinecraftServer server = source.getServer();
		if (server.getUseAllowlist() == on) {
			source.sendFeedback(() -> Text.literal(on ? "Вайтлист уже включён." : "Вайтлист уже выключен."), false);
			return 0;
		}
		server.setUseAllowlist(on);
		if (on) {
			server.kickNonWhitelistedPlayers();
		}
		source.sendFeedback(() -> Text.literal(on ? "Вайтлист включён." : "Вайтлист выключен."), true);
		return 1;
	}

	private static int reload(ServerCommandSource source) {
		source.getServer().getPlayerManager().reloadWhitelist();
		source.sendFeedback(() -> Text.literal("Вайтлист перечитан с диска."), true);
		return 1;
	}


	private static String records(int n) {
		int mod100 = n % 100;
		int mod10 = n % 10;
		if (mod100 >= 11 && mod100 <= 14) {
			return "записей";
		}
		if (mod10 == 1) {
			return "запись";
		}
		if (mod10 >= 2 && mod10 <= 4) {
			return "записи";
		}
		return "записей";
	}

	private static void save(Whitelist wl) {
		try {
			wl.save();
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("whitelist: failed to save whitelist.json: {}", t.toString());
		}
	}

	private static void feedback(MinecraftServer server, ServerCommandSource source, String text) {
		server.execute(() -> {
			try {
				source.sendFeedback(() -> Text.literal(text), true);
			} catch (Throwable ignored) {
			}
		});
	}

	private static List<String> onlineNotWhitelisted(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		Whitelist wl = server.getPlayerManager().getWhitelist();
		List<String> out = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			String n = p.getGameProfile().name();
			if (!wl.isAllowed(PlayerConfigEntry.fromNickname(n))) {
				out.add(n);
			}
		}
		return out;
	}

	private static List<String> whitelistedNames(ServerCommandSource source) {
		Whitelist wl = source.getServer().getPlayerManager().getWhitelist();
		Map<String, String> byLower = new TreeMap<>();
		for (WhitelistEntry e : wl.values()) {
			String n = e.getKey().name();
			byLower.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
		}
		return new ArrayList<>(byLower.values());
	}

	private static NameId mojangLookup(String name) throws Exception {
		String url = MOJANG + URLEncoder.encode(name, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "instantlore-whitelist")
				.GET()
				.build();
		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		int code = resp.statusCode();
		if (code == 204 || code == 404 || code == 400) {
			return null;
		}
		if (code != 200) {
			throw new IllegalStateException("Mojang HTTP " + code);
		}
		JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
		String id = obj.get("id").getAsString();
		String canon = obj.get("name").getAsString();
		return new NameId(dashify(id), canon);
	}

	private static UUID dashify(String hex32) {
		return UUID.fromString(hex32.replaceFirst(
				"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
				"$1-$2-$3-$4-$5"));
	}

	private record NameId(UUID id, String name) {}
}
