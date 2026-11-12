package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreReportBus;
import com.rufana.instantlore.server.PlayerEvents;
import com.rufana.instantlore.server.geoguessr.GeoChunkKey;
import com.rufana.instantlore.server.geoguessr.GeoDimension;
import com.rufana.instantlore.server.geoguessr.IslandSlots;
import com.rufana.instantlore.server.geoguessr.PointCatalog;
import com.rufana.instantlore.server.geoguessr.WarmZoneSampler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GeoGuessrModule implements LoreModule {

	private static final String HOME_KEY = "geohome";

	private int rounds = 5;
	private int roundSeconds = 120;
	private int resultSeconds = 20;
	private double scale = 3000.0;
	private int maxPoints = 1000;
	private String defaultDifficulty = "standard";
	private final Map<String, Integer> difficulties = new LinkedHashMap<>();

	private MinecraftServer server;
	private WarmZoneSampler sampler;
	private PointCatalog catalog;
	private IslandSlots slots;
	private final Random rnd = new Random();
	private Match match;

	private static final int STAGE_X = 0, STAGE_Y = 120, STAGE_Z = 0;
	private static final int STAGE_R = 5;
	private boolean stagingBuilt;

	private record Guess(boolean timeout, double gx, double gz) {
	}

	private static final class Ret {
		net.minecraft.registry.RegistryKey<net.minecraft.world.World> world;
		double x, y, z;
		float yaw, pitch;
		GameMode mode;
	}

	private final class Match {
		final List<UUID> roster = new ArrayList<>();
		final Map<UUID, String> names = new HashMap<>();
		final Map<UUID, Ret> ret = new HashMap<>();
		final Map<UUID, Integer> scores = new HashMap<>();
		final Map<UUID, Guess> guesses = new HashMap<>();
		UUID host;
		int half;
		int idx;
		long deadlineMs;
		long resultDeadlineMs;
		boolean roundClosed;
		boolean advancing;
		final java.util.Set<UUID> skipVotes = new java.util.HashSet<>();
		final java.util.Set<UUID> nextVotes = new java.util.HashSet<>();
		IslandSlots.SlotState slot;
		JsonObject lastResultBase;
		CompletableFuture<IslandSlots.SlotState> current;
		CompletableFuture<IslandSlots.SlotState> next;
	}

	public GeoGuessrModule() {
		difficulties.put("easy", 12);
		difficulties.put("standard", 8);
		difficulties.put("hard", 5);

		LoreReportBus.subscribe(this::onReport);
		ServerTickEvents.END_SERVER_TICK.register(this::onTick);

		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			if (slots != null) {
				slots.shutdown();
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
			if (match == null) {
				return;
			}
			UUID u = handler.getPlayer().getUuid();
			if (!match.roster.contains(u)) {
				return;
			}
			UUID firstOnline = firstOnlineExcept(match, u);
			if (firstOnline == null) {
				endMatch();
				return;
			}
			if (u.equals(match.host)) {
				match.host = firstOnline;
				ServerPlayerEntity nh = server.getPlayerManager().getPlayer(match.host);
				if (nh != null) {
					PlayerEvents.send(nh, "geo.host", "{}");
				}
			}
			if (!match.roundClosed && allSubmitted(u)) {
				closeRound();
			}
		});

		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (match != null && entity instanceof ServerPlayerEntity sp && match.roster.contains(sp.getUuid())) {
				sp.setHealth(sp.getMaxHealth());
				sp.getHungerManager().setFoodLevel(20);
				sp.extinguish();
				var ab = sp.getAbilities();
				ab.allowFlying = true;
				ab.invulnerable = true;
				sp.sendAbilitiesUpdate();
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING,
						StatusEffectInstance.INFINITE, 0, false, false, false));
				if (match.slot != null) {
					teleportToPad(sp, match.slot);
				}
				return false;
			}
			return true;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			ServerPlayerEntity p = handler.getPlayer();
			boolean isPlayer = match != null && match.roster.contains(p.getUuid());
			boolean isHost = match != null && p.getUuid().equals(match.host);
			if (isPlayer || isHost) {
				resyncPlayer(p, isPlayer);
				return;
			}
			if (p.getEntityWorld().getRegistryKey().equals(GeoDimension.KEY)) {
				ServerWorld over = srv.getWorld(net.minecraft.world.World.OVERWORLD);
				if (over != null) {
					net.minecraft.util.math.BlockPos s = over.getSpawnPoint().getPos();
					p.teleportTo(new TeleportTarget(over, Vec3d.ofBottomCenter(s),
							Vec3d.ZERO, p.getYaw(), p.getPitch(), TeleportTarget.NO_OP));
				}
				clearHome(p);
				p.removeStatusEffect(StatusEffects.WATER_BREATHING);
				GameMode def = srv.getDefaultGameMode();
				p.changeGameMode(def);
				if (def != GameMode.CREATIVE && def != GameMode.SPECTATOR) {
					var ab = p.getAbilities();
					ab.allowFlying = false;
					ab.flying = false;
					ab.invulnerable = false;
				}
				p.sendAbilitiesUpdate();
				InstantLore.LOGGER.warn("geoguessr: {} found in the geo dimension outside a match - returned to the world",
						p.getName().getString());
			}
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || match == null) {
				return ActionResult.PASS;
			}
			if (!match.roster.contains(sp.getUuid()) || !isHomeItem(sp.getStackInHand(hand))
					|| match.slot == null) {
				return ActionResult.PASS;
			}
			teleportToPad(sp, match.slot);
			return ActionResult.SUCCESS;
		});
	}

	@Override
	public String id() {
		return "geoguessr";
	}

	@Override
	public String title() {
		return "Геогессер";
	}

	@Override
	public void configure(JsonObject config) {
		if (config == null) {
			return;
		}
		rounds = readInt(config, "rounds", rounds, 1);
		roundSeconds = readInt(config, "round_seconds", roundSeconds, 0);
		resultSeconds = readInt(config, "result_seconds", resultSeconds, 0);
		maxPoints = readInt(config, "max_points", maxPoints, 1);
		if (config.has("scale")) {
			try {
				scale = Math.max(1.0, config.get("scale").getAsDouble());
			} catch (Exception ignored) {
			}
		}
		if (config.has("default_difficulty")) {
			try {
				defaultDifficulty = config.get("default_difficulty").getAsString();
			} catch (Exception ignored) {
			}
		}
		if (config.has("difficulties") && config.get("difficulties").isJsonObject()) {
			JsonObject d = config.getAsJsonObject("difficulties");
			Map<String, Integer> parsed = new LinkedHashMap<>();
			for (String k : d.keySet()) {
				try {
					parsed.put(k, Math.max(1, Math.min(15, d.get(k).getAsInt())));
				} catch (Exception ignored) {
				}
			}
			if (!parsed.isEmpty()) {
				difficulties.clear();
				difficulties.putAll(parsed);
			}
		}
	}

	private static int readInt(JsonObject o, String key, int def, int min) {
		if (o.has(key)) {
			try {
				return Math.max(min, o.get(key).getAsInt());
			} catch (Exception ignored) {
			}
		}
		return def;
	}

	@Override
	public String describe() {
		return "раундов " + rounds + ", " + roundSeconds + "с, сложности " + difficulties.keySet()
				+ (sampler != null && sampler.available() ? ", тёплых точек " + sampler.size()
				: ", источник s1 НЕ загружен");
	}


	private boolean ensureReady(MinecraftServer srv) {
		this.server = srv;
		if (sampler == null) {
			sampler = WarmZoneSampler.loadOrNull();
			catalog = PointCatalog.load();
		}
		if (slots == null && sampler != null) {
			slots = new IslandSlots(srv, catalog);
		}
		return sampler != null && sampler.available()
				&& srv.getWorld(GeoDimension.KEY) != null;
	}

	public boolean start(ServerPlayerEntity host, List<ServerPlayerEntity> players, String difficulty) {
		if (match != null) {
			return false;
		}
		if (players.isEmpty()) {
			return false;
		}
		ServerPlayerEntity lead = host != null ? host : players.get(0);
		if (!ensureReady(lead.getEntityWorld().getServer())) {
			return false;
		}
		List<ServerPlayerEntity> roster = players;
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		if (geo != null) {
			geo.setWeather(Integer.MAX_VALUE, 0, false, false);
		}
		Integer half = difficulties.get(difficulty != null ? difficulty : defaultDifficulty);
		if (half == null) {
			half = difficulties.getOrDefault(defaultDifficulty, 8);
		}

		Match m = new Match();
		m.host = lead.getUuid();
		m.half = half;
		for (ServerPlayerEntity p : roster) {
			if (!m.roster.contains(p.getUuid())) {
				m.roster.add(p.getUuid());
				m.names.put(p.getUuid(), p.getName().getString());
				m.scores.put(p.getUuid(), 0);
				saveReturn(m, p);
				enterPlayer(p);
			}
		}
		if (m.roster.isEmpty()) {
			return false;
		}
		this.match = m;
		InstantLore.LOGGER.info("geoguessr: match started - players {}, rounds {}, difficulty {}",
				m.roster.size(), rounds, difficulty != null ? difficulty : defaultDifficulty);
		for (ServerPlayerEntity p : roster) {
			teleportToStaging(p);
		}
		beginRound();
		return true;
	}

	public boolean stop() {
		if (match == null) {
			return false;
		}
		endMatch();
		return true;
	}

	public boolean active() {
		return match != null;
	}

	public boolean hasDifficulty(String name) {
		return name != null && difficulties.containsKey(name);
	}

	public java.util.Set<String> difficultyNames() {
		return difficulties.keySet();
	}

	private void enterPlayer(ServerPlayerEntity p) {
		applyMatchState(p);
		giveHomeItem(p);
	}

	private void applyMatchState(ServerPlayerEntity p) {
		p.changeGameMode(GameMode.ADVENTURE);
		var ab = p.getAbilities();
		ab.allowFlying = true;
		ab.invulnerable = true;
		p.sendAbilitiesUpdate();
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING,
				StatusEffectInstance.INFINITE, 0, false, false, false));
	}

	private void giveHomeItem(ServerPlayerEntity p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (isHomeItem(inv.getStack(i))) {
				return;
			}
		}
		inv.offerOrDrop(homeItem());
	}

	private void resyncPlayer(ServerPlayerEntity p, boolean isPlayer) {
		Match m = match;
		if (m == null) {
			return;
		}
		if (isPlayer) {
			applyMatchState(p);
			giveHomeItem(p);
			if (m.slot != null) {
				teleportToPad(p, m.slot);
			} else {
				teleportToStaging(p);
			}
		}
		if (m.roundClosed && m.lastResultBase != null) {
			JsonObject out = m.lastResultBase.deepCopy();
			out.addProperty("host", p.getUuid().equals(m.host));
			PlayerEvents.send(p, "geo.result", out.toString());
		} else if (!m.roundClosed && m.slot != null) {
			int secs = m.deadlineMs > 0
					? Math.max(1, (int) ((m.deadlineMs - System.currentTimeMillis()) / 1000L)) : 0;
			PlayerEvents.send(p, "geo.round", roundJson(m, secs, p.getUuid()));
		}
	}

	private void saveReturn(Match m, ServerPlayerEntity p) {
		Ret r = new Ret();
		r.world = p.getEntityWorld().getRegistryKey();
		r.x = p.getX();
		r.y = p.getY();
		r.z = p.getZ();
		r.yaw = p.getYaw();
		r.pitch = p.getPitch();
		r.mode = p.interactionManager.getGameMode();
		m.ret.put(p.getUuid(), r);
	}

	private void endMatch() {
		Match m = match;
		match = null;
		if (m == null) {
			return;
		}
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		for (UUID u : m.roster) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
			if (p != null) {
				PlayerEvents.send(p, "geo.end", "{}");
				restorePlayer(p, m.ret.get(u));
			}
		}
		if (m.host != null && !m.roster.contains(m.host)) {
			ServerPlayerEntity h = server.getPlayerManager().getPlayer(m.host);
			if (h != null) {
				PlayerEvents.send(h, "geo.end", "{}");
			}
		}
		if (slots != null && geo != null) {
			slots.releaseAll(geo);
			releaseWhenDone(m.next, geo);
		}
	}

	private void leaveMatch(ServerPlayerEntity p) {
		Match m = match;
		if (m == null) {
			return;
		}
		UUID u = p.getUuid();
		boolean isPlayer = m.roster.contains(u);
		boolean isHost = u.equals(m.host);
		if (!isPlayer && !isHost) {
			return;
		}
		PlayerEvents.send(p, "geo.end", "{}");
		if (isPlayer) {
			restorePlayer(p, m.ret.get(u));
			m.roster.remove(u);
			m.names.remove(u);
			m.scores.remove(u);
			m.guesses.remove(u);
			m.ret.remove(u);
			m.skipVotes.remove(u);
		}
		if (isHost) {
			UUID nh = firstOnlineExcept(m, u);
			m.host = nh;
			if (nh != null) {
				ServerPlayerEntity h = server.getPlayerManager().getPlayer(nh);
				if (h != null) {
					PlayerEvents.send(h, "geo.host", "{}");
				}
			}
		}
		if (onlineViewers(m) == 0) {
			endMatch();
		}
	}

	private int onlineViewers(Match m) {
		int n = onlineCount(m);
		if (m.host != null && !m.roster.contains(m.host)
				&& server.getPlayerManager().getPlayer(m.host) != null) {
			n++;
		}
		return n;
	}

	private void releaseWhenDone(CompletableFuture<IslandSlots.SlotState> f, ServerWorld geo) {
		if (f == null || slots == null) {
			return;
		}
		f.whenComplete((slot, err) -> server.execute(() -> {
			if (slot != null && slots != null) {
				slots.release(geo, slot.slot());
			}
		}));
	}

	private void restorePlayer(ServerPlayerEntity p, Ret r) {
		clearHome(p);
		p.removeStatusEffect(StatusEffects.WATER_BREATHING);

		if (r != null && r.world != null) {
			ServerWorld back = server.getWorld(r.world);
			if (back != null) {
				p.teleportTo(new TeleportTarget(back, new Vec3d(r.x, r.y, r.z),
						Vec3d.ZERO, r.yaw, r.pitch, TeleportTarget.NO_OP));
			}
		}
		GameMode mode = (r != null && r.mode != null) ? r.mode : server.getDefaultGameMode();
		p.changeGameMode(mode);
		if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
			var ab = p.getAbilities();
			ab.allowFlying = false;
			ab.flying = false;
			ab.invulnerable = false;
		}
		p.sendAbilitiesUpdate();
	}


	private void beginRound() {
		Match m = match;
		IslandSlots.LoadProgress prog = (done, total) -> {
			if (match == m) {
				forEachViewer(m, p -> PlayerEvents.send(p, "geo.loading",
						"{\"done\":" + done + ",\"total\":" + total + "}"));
			}
		};
		boolean fresh = m.current == null;
		if (fresh) {
			forEachViewer(m, p -> PlayerEvents.send(p, "geo.loading", "{\"done\":0,\"total\":256}"));
		}
		CompletableFuture<IslandSlots.SlotState> island =
				(m.current != null) ? m.current : generateIsland(m.half, prog);
		m.current = island;
		island.whenComplete((slot, err) -> server.execute(() -> {
			if (match != m) {
				ServerWorld geo = server.getWorld(GeoDimension.KEY);
				if (slot != null && slots != null && geo != null) {
					slots.release(geo, slot.slot());
				}
				return;
			}
			m.current = null;
			if (err != null || slot == null) {
				InstantLore.LOGGER.error("geoguessr: island for round {} not ready - match aborted", m.idx, err);
				endMatch();
				return;
			}
			m.slot = slot;
			InstantLore.LOGGER.info("geoguessr: round {}/{}", m.idx + 1, rounds);
			m.guesses.clear();
			m.skipVotes.clear();
			m.roundClosed = false;
			m.advancing = false;
			m.resultDeadlineMs = 0;
			m.deadlineMs = roundSeconds > 0 ? System.currentTimeMillis() + roundSeconds * 1000L : 0;
			forEachOnline(m, p -> teleportToPad(p, slot));
			forEachViewer(m, p -> PlayerEvents.send(p, "geo.round", roundJson(m, roundSeconds, p.getUuid())));
			prewarmNext();
		}));
	}

	private String roundJson(Match m, int secs, UUID viewer) {
		return "{\"n\":" + (m.idx + 1) + ",\"of\":" + rounds + ",\"secs\":" + secs
				+ ",\"host\":" + viewer.equals(m.host)
				+ ",\"play\":" + m.roster.contains(viewer) + "}";
	}

	private void prewarmNext() {
		Match m = match;
		if (m.idx + 1 < rounds && m.next == null) {
			m.next = generateIsland(m.half, null);
		}
	}

	private CompletableFuture<IslandSlots.SlotState> generateIsland(int half, IslandSlots.LoadProgress progress) {
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		OptionalLong key = sampler.pick(rnd, catalog::usedPoint);
		if (key.isEmpty()) {
			catalog.reset();
			key = sampler.pick(rnd, catalog::usedPoint);
		}
		if (key.isEmpty() || geo == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("нет тёплой точки/измерения"));
		}
		long k = key.getAsLong();
		catalog.markUsed(k);
		return slots.generate(geo, k, half, progress);
	}

	private void onReport(ServerPlayerEntity player, String type, JsonObject data) {
		Match m = match;
		if (m == null) {
			return;
		}
		UUID u = player.getUuid();
		boolean isPlayer = m.roster.contains(u);
		boolean isHost = u.equals(m.host);
		if (!isPlayer && !isHost) {
			return;
		}
		switch (type) {
			case "geo.guess" -> {
				if (isPlayer) {
					onGuess(m, player, data);
				}
			}
			case "geo.vote" -> {
				if (isPlayer) {
					if (m.roundClosed) {
						onNextVote(m, player);
					} else {
						onVoteSkip(m, player);
					}
				}
			}
			case "geo.next" -> {
				if (player.getUuid().equals(m.host) && m.roundClosed && !m.advancing) {
					advance();
				}
			}
			case "geo.skip" -> {
				if (player.getUuid().equals(m.host) && !m.roundClosed) {
					closeRound();
				}
			}
			case "geo.done" -> {
				if (player.getUuid().equals(m.host)) {
					endMatch();
				}
			}
			case "geo.leave" -> leaveMatch(player);
			default -> {
			}
		}
	}

	private void onGuess(Match m, ServerPlayerEntity player, JsonObject data) {
		if (m.roundClosed) {
			return;
		}
		UUID u = player.getUuid();
		if (m.guesses.containsKey(u)) {
			return;
		}
		boolean timeout = data != null && data.has("timeout") && data.get("timeout").getAsBoolean();
		if (timeout || data == null || !data.has("x") || !data.has("z")) {
			m.guesses.put(u, new Guess(true, 0, 0));
		} else {
			try {
				double gx = data.get("x").getAsDouble();
				double gz = data.get("z").getAsDouble();
				if (!Double.isFinite(gx) || !Double.isFinite(gz)) {
					m.guesses.put(u, new Guess(true, 0, 0));
				} else {
					double lim = 3.0e7;
					gx = Math.max(-lim, Math.min(lim, gx));
					gz = Math.max(-lim, Math.min(lim, gz));
					m.guesses.put(u, new Guess(false, gx, gz));
				}
			} catch (Exception e) {
				m.guesses.put(u, new Guess(true, 0, 0));
			}
		}
		if (allSubmitted()) {
			closeRound();
		} else {
			int submitted = 0;
			for (UUID ru : m.roster) {
				if (server.getPlayerManager().getPlayer(ru) != null && m.guesses.containsKey(ru)) {
					submitted++;
				}
			}
			int total = onlineCount(m);
			int shown = submitted;
			forEachViewer(m, p -> PlayerEvents.send(p, "geo.waiting",
					"{\"submitted\":" + shown + ",\"total\":" + total + "}"));
		}
	}

	private void onVoteSkip(Match m, ServerPlayerEntity player) {
		if (m.roundClosed || !m.roster.contains(player.getUuid())) {
			return;
		}
		m.skipVotes.add(player.getUuid());
		int votes = 0;
		for (UUID u : m.skipVotes) {
			if (server.getPlayerManager().getPlayer(u) != null) {
				votes++;
			}
		}
		int total = onlineCount(m);
		if (total > 0 && votes * 2 > total) {
			closeRound();
			return;
		}
		int shownV = votes, shownT = total;
		forEachViewer(m, p -> PlayerEvents.send(p, "geo.skipvote",
				"{\"votes\":" + shownV + ",\"total\":" + shownT + "}"));
	}

	private void onNextVote(Match m, ServerPlayerEntity player) {
		if (!m.roundClosed || m.advancing || m.idx >= rounds - 1
				|| !m.roster.contains(player.getUuid())) {
			return;
		}
		m.nextVotes.add(player.getUuid());
		int votes = 0;
		for (UUID u : m.nextVotes) {
			if (server.getPlayerManager().getPlayer(u) != null) {
				votes++;
			}
		}
		int total = onlineCount(m);
		if (total > 0 && votes * 2 > total) {
			advance();
			return;
		}
		int shownV = votes, shownT = total;
		forEachViewer(m, p -> PlayerEvents.send(p, "geo.skipvote",
				"{\"votes\":" + shownV + ",\"total\":" + shownT + "}"));
	}

	private boolean allSubmitted() {
		return allSubmitted(null);
	}

	private boolean allSubmitted(UUID skip) {
		Match m = match;
		if (m == null) {
			return false;
		}
		for (UUID u : m.roster) {
			if (u.equals(skip)) {
				continue;
			}
			if (server.getPlayerManager().getPlayer(u) != null && !m.guesses.containsKey(u)) {
				return false;
			}
		}
		return true;
	}

	private UUID firstOnlineExcept(Match m, UUID except) {
		for (UUID u : m.roster) {
			if (!u.equals(except) && server.getPlayerManager().getPlayer(u) != null) {
				return u;
			}
		}
		return null;
	}

	private int onlineCount(Match m) {
		int n = 0;
		for (UUID u : m.roster) {
			if (server.getPlayerManager().getPlayer(u) != null) {
				n++;
			}
		}
		return n;
	}

	private static final long DEADLINE_GRACE_MS = 1500L;

	private void onTick(MinecraftServer srv) {
		Match m = match;
		if (m == null || m.slot == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!m.roundClosed) {
			if (m.deadlineMs != 0 && now >= m.deadlineMs + DEADLINE_GRACE_MS) {
				closeRound();
			}
		} else if (m.resultDeadlineMs != 0 && !m.advancing && now >= m.resultDeadlineMs) {
			if (m.idx >= rounds - 1) {
				endMatch();
			} else {
				advance();
			}
		}
	}

	private void closeRound() {
		Match m = match;
		if (m == null || m.roundClosed) {
			return;
		}
		m.roundClosed = true;
		m.nextVotes.clear();
		boolean last = m.idx == rounds - 1;
		int ax = m.slot.answerX(), az = m.slot.answerZ();

		JsonArray guesses = new JsonArray();
		Map<UUID, Integer> roundPts = new HashMap<>();
		for (UUID u : m.roster) {
			Guess g = m.guesses.get(u);
			int points = 0;
			JsonObject go = new JsonObject();
			go.addProperty("name", m.names.getOrDefault(u, "?"));
			if (g != null && !g.timeout()) {
				double dist = Math.hypot(g.gx() - ax, g.gz() - az);
				points = (int) Math.round(maxPoints * Math.exp(-dist / scale));
				go.addProperty("gx", (int) Math.round(g.gx()));
				go.addProperty("gz", (int) Math.round(g.gz()));
				go.addProperty("dist", (int) Math.round(dist));
			} else {
				go.addProperty("nog", true);
			}
			go.addProperty("points", points);
			roundPts.put(u, points);
			m.scores.merge(u, points, Integer::sum);
			guesses.add(go);
		}

		JsonArray board = buildBoard(m, roundPts);

		JsonObject base = new JsonObject();
		base.addProperty("n", m.idx + 1);
		base.addProperty("of", rounds);
		base.addProperty("final", last);
		JsonObject truth = new JsonObject();
		truth.addProperty("ax", ax);
		truth.addProperty("az", az);
		base.add("truth", truth);
		base.add("guesses", guesses);
		base.add("board", board);
		base.addProperty("auto", resultSeconds);
		m.lastResultBase = base;

		m.resultDeadlineMs = resultSeconds > 0 ? System.currentTimeMillis() + resultSeconds * 1000L : 0;

		forEachViewer(m, p -> {
			JsonObject out = base.deepCopy();
			out.addProperty("host", p.getUuid().equals(m.host));
			PlayerEvents.send(p, "geo.result", out.toString());
		});
	}

	private JsonArray buildBoard(Match m, Map<UUID, Integer> roundPts) {
		List<UUID> order = new ArrayList<>(m.roster);
		order.sort((a, b) -> Integer.compare(m.scores.getOrDefault(b, 0), m.scores.getOrDefault(a, 0)));
		JsonArray board = new JsonArray();
		for (UUID u : order) {
			JsonObject row = new JsonObject();
			row.addProperty("name", m.names.getOrDefault(u, "?"));
			row.addProperty("total", m.scores.getOrDefault(u, 0));
			row.addProperty("round", roundPts.getOrDefault(u, 0));
			board.add(row);
		}
		return board;
	}

	private void advance() {
		Match m = match;
		if (m.idx >= rounds - 1) {
			endMatch();
			return;
		}
		m.advancing = true;
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		if (slots != null && geo != null && m.slot != null) {
			slots.release(geo, m.slot.slot());
		}
		m.idx++;
		m.current = m.next;
		m.next = null;
		beginRound();
	}

	private void forEachOnline(Match m, java.util.function.Consumer<ServerPlayerEntity> fn) {
		for (UUID u : m.roster) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
			if (p != null) {
				fn.accept(p);
			}
		}
	}

	private void forEachViewer(Match m, java.util.function.Consumer<ServerPlayerEntity> fn) {
		forEachOnline(m, fn);
		if (m.host != null && !m.roster.contains(m.host)) {
			ServerPlayerEntity h = server.getPlayerManager().getPlayer(m.host);
			if (h != null) {
				fn.accept(h);
			}
		}
	}

	private void ensureStaging(ServerWorld geo) {
		if (stagingBuilt) {
			return;
		}
		net.minecraft.util.math.BlockPos.Mutable p = new net.minecraft.util.math.BlockPos.Mutable();
		for (int dx = -STAGE_R; dx <= STAGE_R; dx++) {
			for (int dz = -STAGE_R; dz <= STAGE_R; dz++) {
				boolean center = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				geo.setBlockState(p.set(STAGE_X + dx, STAGE_Y, STAGE_Z + dz),
						(center ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ).getDefaultState());
			}
		}
		stagingBuilt = true;
		InstantLore.LOGGER.debug("geoguessr: waiting platform built in geo ({}/{}/{})",
				STAGE_X, STAGE_Y, STAGE_Z);
	}

	private void teleportToStaging(ServerPlayerEntity p) {
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		if (geo == null) {
			return;
		}
		ensureStaging(geo);
		p.teleportTo(new TeleportTarget(geo, new Vec3d(STAGE_X + 0.5, STAGE_Y + 1, STAGE_Z + 0.5),
				Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
	}

	private void teleportToPad(ServerPlayerEntity p, IslandSlots.SlotState slot) {
		ServerWorld geo = server.getWorld(GeoDimension.KEY);
		if (geo == null) {
			InstantLore.LOGGER.warn("geoguessr: no dimension - {} NOT moved to the pad", p.getName().getString());
			return;
		}
		InstantLore.LOGGER.debug("geoguessr: moving {} to pad {}/{}/{} (slot {})",
				p.getName().getString(), slot.padX(), slot.padY(), slot.padZ(), slot.slot());
		p.teleportTo(new TeleportTarget(geo, new Vec3d(slot.padX() + 0.5, slot.padY(), slot.padZ() + 0.5),
				Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
	}


	private static ItemStack homeItem() {
		ItemStack stack = new ItemStack(Items.RECOVERY_COMPASS);
		stack.set(DataComponentTypes.CUSTOM_NAME,
				Text.literal("✦ Вернуться к началу").formatted(Formatting.AQUA));
		NbtCompound nbt = new NbtCompound();
		nbt.putString(HOME_KEY, "1");
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return stack;
	}

	private static boolean isHomeItem(ItemStack stack) {
		NbtComponent d = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
		return d != null && "1".equals(d.copyNbt().getString(HOME_KEY, ""));
	}

	private static void clearHome(ServerPlayerEntity player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (isHomeItem(inv.getStack(i))) {
				inv.setStack(i, ItemStack.EMPTY);
			}
		}
	}
}
