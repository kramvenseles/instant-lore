package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreBlockBus;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.PlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TourModule implements LoreModule {

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("tour.json");

	private static final int TPS = 20;

	private static final int GRACE_TICKS = 15 * TPS;

	private static final int MAX_STOPS = 16;

	private final RiteModule rite;

	private String cutsceneId = "orbit";
	private double stopSeconds = 6.0;
	private double fadeSeconds = 0.6;
	private int cooldownSeconds = 120;
	private int maxConcurrent = 4;
	private String riteName = "";
	private String finalTitle = "";
	private String finalSubtitle = "";
	private String answerTitle = "";
	private String answerSubtitle = "";
	private String answerEvent = "";
	private final Map<String, Tour> tours = new java.util.LinkedHashMap<>();

	private final Map<String, Moment> moments = new java.util.LinkedHashMap<>();

	private record Moment(String name, String fade, List<Caption> captions,
			boolean initiates, String hud, String show) {
	}

	private int[] minVersion = {0, 77, 0};

	private String outdatedMessage =
			"Твой мод устарел — закрой и открой Minecraft, обновление приедет само.";

	private record Tour(String name, List<Button> buttons, List<Stop> stops,
			boolean moveBody, String hud, String perspective) {
	}

	private record Stop(String dimension, BlockPos pos, float yaw, String title, String subtitle,
			String cutscene, BlockPos lightning, int lightningPeriod, double seconds,
			List<Caption> captions, String body) {
	}

	private record Caption(double at, String title, String subtitle, int stayTicks) {
	}

	private record Button(String dimension, BlockPos pos) {
	}

	private final Map<UUID, Run> running = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastStarted = new ConcurrentHashMap<>();

	private final Map<UUID, String> initiates = new ConcurrentHashMap<>();

	private final Map<UUID, Long> hudUntil = new ConcurrentHashMap<>();

	private long ticks;

	private final Set<UUID> perspectiveDebt = ConcurrentHashMap.newKeySet();

	private static final class Run {
		UUID player;
		String backDimension;
		double backX;
		double backY;
		double backZ;
		float backYaw;
		float backPitch;
		GameMode backMode;
		String tour;
		int tick;
		int stop = -1;
		int nextAt;
		int stopStartedAt;
		int captionsShown;
	}

	public TourModule(RiteModule rite) {
		this.rite = rite;
	}

	@Override
	public String id() {
		return "tour";
	}

	@Override
	public String title() {
		return "Туры по точкам";
	}

	@Override
	public void configure(JsonObject config) {
		cutsceneId = config.has("cutscene") ? config.get("cutscene").getAsString() : "orbit";
		stopSeconds = clamp(readDouble(config, "stop_seconds", 6.0), 1.0, 60.0);
		fadeSeconds = clamp(readDouble(config, "fade_seconds", 0.6), 0.0, 5.0);
		cooldownSeconds = (int) clamp(readDouble(config, "cooldown_seconds", 120), 0, 3600);
		maxConcurrent = (int) clamp(readDouble(config, "max_concurrent", 4), 1, 32);
		riteName = config.has("rite") ? config.get("rite").getAsString() : "";
		finalTitle = config.has("final_title") ? config.get("final_title").getAsString() : "";
		finalSubtitle = config.has("final_subtitle") ? config.get("final_subtitle").getAsString() : "";
		answerTitle = config.has("answer_title") ? config.get("answer_title").getAsString() : "";
		answerSubtitle = config.has("answer_subtitle") ? config.get("answer_subtitle").getAsString() : "";
		answerEvent = config.has("answer_event") ? config.get("answer_event").getAsString() : "";

		tours.clear();
		if (config.has("tours") && config.get("tours").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : config.getAsJsonObject("tours").entrySet()) {
				if (entry.getValue().isJsonObject()) {
					tours.put(entry.getKey(), tourOf(entry.getKey(), entry.getValue().getAsJsonObject()));
				}
			}
		}
		minVersion = versionOf(config.has("min_version") ? config.get("min_version").getAsString() : "0.77.0");
		outdatedMessage = config.has("outdated_message")
				? config.get("outdated_message").getAsString() : outdatedMessage;

		moments.clear();
		if (config.has("moments") && config.get("moments").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : config.getAsJsonObject("moments").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject o = entry.getValue().getAsJsonObject();
				moments.put(entry.getKey(), new Moment(entry.getKey(),
						o.has("fade") ? o.get("fade").toString() : "",
						captionsOf(o),
						!o.has("initiates") || o.get("initiates").getAsBoolean(),
						o.has("hud") ? o.get("hud").getAsString() : "",
						o.has("show") ? o.get("show").getAsString() : ""));
			}
		}

		if (config.has("stops") && config.get("stops").isJsonArray()) {
				tours.put("main", tourOf("main", config));
		}
	}

	private Tour tourOf(String name, JsonObject config) {
		List<Stop> stops = new ArrayList<>();
		if (config.has("stops") && config.get("stops").isJsonArray()) {
			for (JsonElement element : config.getAsJsonArray("stops")) {
				if (!element.isJsonObject() || stops.size() >= MAX_STOPS) {
					continue;
				}
				JsonObject o = element.getAsJsonObject();
				stops.add(new Stop(
						o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
						new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()),
						o.has("yaw") ? o.get("yaw").getAsFloat() : 0.0f,
						o.has("title") ? o.get("title").getAsString() : "",
						o.has("subtitle") ? o.get("subtitle").getAsString() : "",
						o.has("cutscene") ? o.get("cutscene").getAsString() : "",
						o.has("lightning") && o.get("lightning").isJsonObject()
								? new BlockPos(o.getAsJsonObject("lightning").get("x").getAsInt(),
										o.getAsJsonObject("lightning").get("y").getAsInt(),
										o.getAsJsonObject("lightning").get("z").getAsInt())
								: null,
						o.has("lightning") && o.getAsJsonObject("lightning").has("period")
								? Math.max(1, o.getAsJsonObject("lightning").get("period").getAsInt())
								: 20,
						clamp(readDouble(o, "seconds", 0), 0, 60),
						captionsOf(o),
						o.has("body") ? o.get("body").getAsString() : ""));
			}
		}

		List<Button> buttons = new ArrayList<>();
		if (config.has("buttons") && config.get("buttons").isJsonArray()) {
			for (JsonElement element : config.getAsJsonArray("buttons")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject o = element.getAsJsonObject();
				buttons.add(new Button(
						o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld",
						new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt())));
			}
		}
		return new Tour(name, buttons, stops,
				!config.has("move_body") || config.get("move_body").getAsBoolean(),
				config.has("hud") ? config.get("hud").getAsString() : "",
				config.has("perspective") ? config.get("perspective").getAsString() : "");
	}

	private static List<Caption> captionsOf(JsonObject stop) {
		List<Caption> out = new ArrayList<>();
		if (!stop.has("captions") || !stop.get("captions").isJsonArray()) {
			return out;
		}
		for (JsonElement element : stop.getAsJsonArray("captions")) {
			if (!element.isJsonObject() || out.size() >= 12) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			out.add(new Caption(
					clamp(readDouble(o, "at", 0), 0, 60),
					o.has("title") ? o.get("title").getAsString() : "",
					o.has("subtitle") ? o.get("subtitle").getAsString() : "",
					(int) clamp(readDouble(o, "stay", 70), 5, 400)));
		}
		out.sort((a, b) -> Double.compare(a.at(), b.at()));
		return out;
	}

	@Override
	public String describe() {
		StringBuilder sb = new StringBuilder("туров " + tours.size());
		for (Tour tour : tours.values()) {
			sb.append("; ").append(tour.name()).append(": остановок ").append(tour.stops().size())
					.append(", кнопок ").append(tour.buttons().size());
		}
		sb.append(", кулдаун ").append(cooldownSeconds).append(" с");
		if (!running.isEmpty()) {
			sb.append(", смотрят ").append(running.size());
		}
		return sb.toString();
	}

	@Override
	public void onDisable(MinecraftServer server) {
		for (UUID uuid : new ArrayList<>(running.keySet())) {
			finish(server, uuid, "модуль выключен");
		}
	}


	public void subscribe() {
		LoreBlockBus.subscribeUse((player, hand, stack, world, pos, slot, definition, side, hit, sneaking) -> {
			if (!ModuleRegistry.isActive(id())) {
				return false;
			}
			String dimension = dimensionOf(world);
			for (Tour tour : tours.values()) {
				for (Button button : tour.buttons()) {
					if (button.pos().equals(pos) && button.dimension().equals(dimension)) {
						start(player, tour.name(), false);
						return true;
					}
				}
			}
			return false;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID uuid = handler.getPlayer().getUuid();
			if (running.containsKey(uuid) && !perspectiveOf(running.get(uuid)).isBlank()) {
				perspectiveDebt.add(uuid);
			}
			finish(server, uuid, "разрыв связи");
		});

		com.rufana.instantlore.server.OfferingEvents.subscribe(
				(world, pos, type, title, by, need, have) -> {
					if (ModuleRegistry.isActive(id())) {
						fire(world.getServer(), "goal", title);
					}
				});

		com.rufana.instantlore.server.OfferingEvents.subscribeRite(
				(server, rite, kind, type, dimension, pos, by) -> {
					if (ModuleRegistry.isActive(id()) && "core".equals(kind)) {
						fire(server, "gate", rite);
					}
				});
	}

	public String fire(MinecraftServer server, String name, String subject) {
		Moment moment = moments.get(name);
		if (moment == null) {
			return "нет момента «" + name + "»; есть: "
					+ (moments.isEmpty() ? "ни одного" : String.join(", ", moments.keySet()));
		}
		int sent = 0;
		int shown = 0;
		int outdated = 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (moment.initiates() && !initiates.containsKey(player.getUuid())) {
				continue;
			}
			if (!moment.show().isBlank() && canWatch(player)) {
				String refused = start(player, moment.show(), true);
				if (refused == null) {
					shown++;
					continue;
				}
				if ("мод устарел".equals(refused)) {
					outdated++;
					continue;
				}
			}
			if (!moment.fade().isBlank()) {
				PlayerEvents.send(player, "screen.fade", moment.fade());
			}
			for (Caption caption : moment.captions()) {
				String line1 = fill(server, caption.title(), subject);
				String line2 = fill(server, caption.subtitle(), subject);
				if (moment.hud().isBlank()) {
					PlayerEvents.send(player, "title.show", "{\"title\":\"" + escape(line1) + "\""
							+ ",\"subtitle\":\"" + escape(line2) + "\""
							+ ",\"fade_in\":10,\"stay\":" + caption.stayTicks() + ",\"fade_out\":20}");
				} else {
					PlayerEvents.send(player, "hud.show", "{\"id\":\"" + escape(moment.hud()) + "\""
							+ ",\"values\":{\"line1\":\"" + escape(line1) + "\""
							+ ",\"line2\":\"" + escape(line2) + "\"}}");
					hudUntil.put(player.getUuid(), ticks + caption.stayTicks());
				}
			}
			sent++;
		}
		LoreDevLog.info("[tour] moment '{}' ({}): notice {}, showing {}, stale {}",
				name, subject, sent, shown, outdated);
		return null;
	}

	private String fill(MinecraftServer server, String text, String subject) {
		if (text.isEmpty()) {
			return text;
		}
		String out = text.replace("${жертвенник}", subject == null ? "" : subject);
		if (!riteName.isBlank() && (out.contains("${") )) {
			int quorum = rite.quorumOf(riteName);
			int filled = rite.filledOf(server, riteName);
			int total = rite.sizeOf(riteName);
			out = out.replace("${кворум}", String.valueOf(Math.max(quorum, 0)))
					.replace("${набрано}", String.valueOf(Math.max(filled, 0)))
					.replace("${всего}", String.valueOf(Math.max(total, 0)))
					.replace("${осталось}", String.valueOf(Math.max(quorum - filled, 0)));
		}
		return out;
	}

	private static int[] versionOf(String text) {
		String[] parts = text == null ? new String[0] : text.trim().split("\\.");
		int[] out = {0, 77, 0};
		for (int i = 0; i < Math.min(3, parts.length); i++) {
			try {
				out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
			} catch (RuntimeException e) {
				return new int[]{0, 77, 0};
			}
		}
		return out;
	}

	private static boolean canWatch(ServerPlayerEntity player) {
		return player.hurtTime == 0
				&& player.fallDistance < 3.0
				&& !player.isGliding()
				&& !player.isSleeping();
	}

	public List<String> moments() {
		List<String> out = new ArrayList<>();
		moments.forEach((name, moment) -> out.add(name + " — строк " + moment.captions().size()
				+ (moment.initiates() ? ", только посвящённым" : ", всем")));
		return out;
	}

	private void answerUnused(MinecraftServer server, String title, String type) {
		if (answerTitle.isBlank() && answerSubtitle.isBlank()) {
			return;
		}
		String subtitle = answerSubtitle.replace("${жертвенник}", title == null ? "" : title);
		int sent = 0;
		for (UUID uuid : initiates.keySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null) {
				continue;
			}
			PlayerEvents.send(player, "title.show", "{\"title\":\"" + escape(answerTitle) + "\""
					+ ",\"subtitle\":\"" + escape(subtitle) + "\""
					+ ",\"fade_in\":10,\"stay\":50,\"fade_out\":20}");
			if (!answerEvent.isBlank()) {
				PlayerEvents.send(player, answerEvent, "{\"offering\":\"" + escape(type) + "\""
						+ ",\"title\":\"" + escape(title == null ? "" : title) + "\"}");
			}
			sent++;
		}
		LoreDevLog.info("[tour] response to '{}' sent to initiates: {} of {}",
				title, sent, initiates.size());
	}

	public String start(ServerPlayerEntity player, String tourName, boolean force) {
		Tour tour = tours.get(tourName == null || tourName.isBlank() ? firstTour() : tourName);
		if (tour == null) {
			return "нет такого показа; есть: "
					+ (tours.isEmpty() ? "ни одного" : String.join(", ", tours.keySet()));
		}
		if (tour.stops().isEmpty()) {
			return "у показа «" + tour.name() + "» нет остановок";
		}
		if (!com.rufana.instantlore.server.ClientVersions.meetsMinimum(
				player.getUuid(), minVersion[0], minVersion[1], minVersion[2])) {
			if (!outdatedMessage.isBlank()) {
				player.sendMessage(net.minecraft.text.Text.literal(outdatedMessage)
						.formatted(net.minecraft.util.Formatting.RED), false);
			}
			LoreDevLog.info("[tour] {} not admitted to showing '{}': version {}",
					player.getName().getString(), tour.name(),
					com.rufana.instantlore.server.ClientVersions.reported(player.getUuid()));
			return "мод устарел";
		}
		if (running.containsKey(player.getUuid())) {
			return "показ уже идёт";
		}
		if (running.size() >= maxConcurrent) {
			return "сейчас смотрят " + running.size() + " — попробуй позже";
		}
		long now = player.getEntityWorld().getTime();
		Long last = lastStarted.get(player.getUuid());
		if (!force && last != null && now - last < (long) cooldownSeconds * TPS) {
			long left = ((long) cooldownSeconds * TPS - (now - last)) / TPS;
			return "ещё рано — подожди " + left + " с";
		}

		Run run = new Run();
		run.player = player.getUuid();
		run.tour = tour.name();
		run.backDimension = dimensionOf(player.getEntityWorld());
		run.backX = player.getX();
		run.backY = player.getY();
		run.backZ = player.getZ();
		run.backYaw = player.getYaw();
		run.backPitch = player.getPitch();
		run.backMode = player.interactionManager.getGameMode();
		run.nextAt = 0;

		running.put(player.getUuid(), run);
		lastStarted.put(player.getUuid(), now);
		initiates.put(player.getUuid(), player.getName().getString());
		saveInitiates();
		save();

		if (!tour.hud().isBlank()) {
			PlayerEvents.send(player, "hud.show", "{\"id\":\"" + escape(tour.hud())
					+ "\",\"values\":{\"line1\":\"\",\"line2\":\"\"}}");
		}
		LoreDevLog.info("[tour] {} started showing '{}' ({} stops)",
				player.getName().getString(), tour.name(), tour.stops().size());
		return null;
	}

	public boolean stop(MinecraftServer server, UUID uuid) {
		return finish(server, uuid, "остановлено");
	}

	public List<String> list(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<UUID, Run> entry : running.entrySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			out.add((player == null ? entry.getKey().toString() : player.getName().getString())
					+ " — «" + entry.getValue().tour + "» остановка " + (entry.getValue().stop + 1)
					+ "/" + stopsOf(entry.getValue()).size());
		}
		return out;
	}


	@Override
	public void tick(MinecraftServer server) {
		ticks++;
		if (!hudUntil.isEmpty()) {
			for (Map.Entry<UUID, Long> entry : new ArrayList<>(hudUntil.entrySet())) {
				if (ticks < entry.getValue()) {
					continue;
				}
				hudUntil.remove(entry.getKey());
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player != null && !running.containsKey(entry.getKey())) {
					PlayerEvents.send(player, "hud.hide", "{}");
				}
			}
		}
		if (running.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Run> entry : new ArrayList<>(running.entrySet())) {
			Run run = entry.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}

			run.tick++;
			if (run.tick > totalTicks(run) + GRACE_TICKS) {
				finish(server, entry.getKey(), "потолок времени");
				continue;
			}

			flash(player, run);
			captions(player, run);
			if (run.tick < run.nextAt) {
				hold(player, run);
				continue;
			}

			int next = run.stop + 1;
			if (next >= stopsOf(run).size()) {
				closing(player);
				finish(server, entry.getKey(), "показ окончен");
				continue;
			}
			enter(player, run, next);
		}
	}

	private void enter(ServerPlayerEntity player, Run run, int index) {
		Stop stop = stopsOf(run).get(index);
		run.stop = index;
		run.stopStartedAt = run.tick;
		run.captionsShown = 0;
		run.nextAt = run.tick + (int) Math.round(secondsOf(stop) * TPS);

		ServerWorld world = worldOf(player.getEntityWorld().getServer(), stop.dimension());
		if (world == null) {
			InstantLore.LOGGER.error("Tour: stop world {} not found", stop.dimension());
			return;
		}

		if (fadeSeconds > 0) {
			PlayerEvents.send(player, "screen.fade", "{\"color\":\"#FF000000\",\"in\":" + fadeSeconds
					+ ",\"hold\":0.15,\"out\":" + fadeSeconds + "}");
		}
		boolean visible = visibleBody(run, stop);
		if (!visible) {
			if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
				player.changeGameMode(GameMode.SPECTATOR);
			}
			player.teleport(world, stop.pos().getX() + 0.5, stop.pos().getY() + 1.0,
					stop.pos().getZ() + 0.5, java.util.Set.of(), stop.yaw(), 0.0f, false);
		} else if (run.backMode != null && player.interactionManager.getGameMode() != run.backMode) {
			player.changeGameMode(run.backMode);
		}
		PlayerEvents.send(player, "cutscene.play", "{\"id\":\""
				+ (stop.cutscene().isBlank() ? cutsceneId : stop.cutscene()) + "\""
				+ ",\"x\":" + (stop.pos().getX() + 0.5)
				+ ",\"y\":" + stop.pos().getY()
				+ ",\"z\":" + (stop.pos().getZ() + 0.5)
				+ ",\"yaw\":" + stop.yaw() + "}");
		if (!hudOf(run).isBlank()) {
			PlayerEvents.send(player, "hud.hide", "{}");
			hudUntil.remove(player.getUuid());
		}

		String perspective = perspectiveOf(run);
		if (!perspective.isBlank()) {
			PlayerEvents.send(player, "camera.perspective",
					"{\"mode\":\"" + escape(visible ? perspective : "first") + "\"}");
		}
		if (!stop.title().isBlank() || !stop.subtitle().isBlank()) {
			PlayerEvents.send(player, "title.show", "{\"title\":\"" + escape(stop.title()) + "\""
					+ ",\"subtitle\":\"" + escape(stop.subtitle()) + "\""
					+ ",\"fade_in\":10,\"stay\":" + (int) (secondsOf(stop) * TPS - 30) + ",\"fade_out\":15}");
		}
		LoreDevLog.info("[tour] {} → '{}' stop {}/{} @ {}", player.getName().getString(),
				run.tour, index + 1, stopsOf(run).size(), stop.pos().toShortString());
	}

	private void closing(ServerPlayerEntity player) {
		String subtitle = finalSubtitle;
		int quorum = riteName.isBlank() ? 0 : rite.quorumOf(riteName);
		int members = riteName.isBlank() ? 0 : rite.sizeOf(riteName);
		if (quorum > 0) {
			subtitle = subtitle.replace("${кворум}", String.valueOf(quorum))
					.replace("${всего}", String.valueOf(members));
		}
		PlayerEvents.send(player, "cutscene.stop", "{}");
		if (!finalTitle.isBlank() || !subtitle.isBlank()) {
			PlayerEvents.send(player, "title.show", "{\"title\":\"" + escape(finalTitle) + "\""
					+ ",\"subtitle\":\"" + escape(subtitle) + "\""
					+ ",\"fade_in\":10,\"stay\":80,\"fade_out\":20}");
		}
	}

	private void captions(ServerPlayerEntity player, Run run) {
		if (run.stop < 0 || run.stop >= stopsOf(run).size()) {
			return;
		}
		List<Caption> list = stopsOf(run).get(run.stop).captions();
		while (run.captionsShown < list.size()) {
			Caption caption = list.get(run.captionsShown);
			if (run.tick - run.stopStartedAt < (int) Math.round(caption.at() * TPS)) {
				return;
			}
			run.captionsShown++;
			if (caption.title().isBlank() && caption.subtitle().isBlank()) {
				continue;
			}
			String hud = hudOf(run);
			if (hud.isBlank()) {
				PlayerEvents.send(player, "title.show", "{\"title\":\"" + escape(caption.title()) + "\""
						+ ",\"subtitle\":\"" + escape(caption.subtitle()) + "\""
						+ ",\"fade_in\":8,\"stay\":" + caption.stayTicks() + ",\"fade_out\":12}");
			} else {
				PlayerEvents.send(player, "hud.show", "{\"id\":\"" + escape(hud) + "\""
						+ ",\"values\":{\"line1\":\"" + escape(caption.title()) + "\""
						+ ",\"line2\":\"" + escape(caption.subtitle()) + "\"}}");
			}
		}
	}

	private void flash(ServerPlayerEntity player, Run run) {
		if (run.stop < 0 || run.stop >= stopsOf(run).size()) {
			return;
		}
		Stop stop = stopsOf(run).get(run.stop);
		if (stop.lightning() == null || run.tick % stop.lightningPeriod() != 0) {
			return;
		}
		ServerWorld world = worldOf(player.getEntityWorld().getServer(), stop.dimension());
		if (world == null) {
			return;
		}
		net.minecraft.entity.LightningEntity bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT
				.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.setPosition(stop.lightning().getX() + 0.5, stop.lightning().getY(),
				stop.lightning().getZ() + 0.5);
		bolt.setCosmetic(true);
		world.spawnEntity(bolt);
	}

	private void hold(ServerPlayerEntity player, Run run) {
		if (run.stop < 0 || run.stop >= stopsOf(run).size() || run.tick % 10 != 0) {
			return;
		}
		Stop stop = stopsOf(run).get(run.stop);
		if (visibleBody(run, stop)) {
			return;
		}
		double dx = player.getX() - (stop.pos().getX() + 0.5);
		double dy = player.getY() - (stop.pos().getY() + 1.0);
		double dz = player.getZ() - (stop.pos().getZ() + 0.5);
		if (dx * dx + dy * dy + dz * dz <= 4.0) {
			return;
		}
		ServerWorld world = worldOf(player.getEntityWorld().getServer(), stop.dimension());
		if (world != null) {
			player.teleport(world, stop.pos().getX() + 0.5, stop.pos().getY() + 1.0,
					stop.pos().getZ() + 0.5, java.util.Set.of(), stop.yaw(), 0.0f, false);
		}
	}

	private boolean finish(MinecraftServer server, UUID uuid, String why) {
		Run run = running.remove(uuid);
		if (run == null) {
			return false;
		}
		save();

		ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
		if (player == null) {
			pending.put(uuid, run);
			savePending();
			return true;
		}
		restore(server, player, run);
		LoreDevLog.info("[tour] {} - end of showing ({})", player.getName().getString(), why);
		return true;
	}

	private final Map<UUID, Run> pending = new ConcurrentHashMap<>();

	private void restore(MinecraftServer server, ServerPlayerEntity player, Run run) {
		PlayerEvents.send(player, "cutscene.stop", "{}");
		PlayerEvents.send(player, "screen.fade", "{\"clear\":true}");
		PlayerEvents.send(player, "hud.hide", "{}");
		Tour tour = tours.get(run.tour);
		if (tour != null && !tour.perspective().isBlank()) {
			PlayerEvents.send(player, "camera.perspective", "{\"mode\":\"first\"}");
		}
		ServerWorld world = worldOf(server, run.backDimension);
		if (world != null) {
			player.teleport(world, run.backX, run.backY, run.backZ, java.util.Set.of(),
					run.backYaw, run.backPitch, false);
		}
		if (run.backMode != null) {
			player.changeGameMode(run.backMode);
		}
	}

	@Override
	public void onEnable(MinecraftServer server) {
		load();
		loadInitiates();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			PlayerEvents.send(handler.getPlayer(), "cutscene.stop", "{}");
			PlayerEvents.send(handler.getPlayer(), "hud.hide", "{}");
			PlayerEvents.send(handler.getPlayer(), "screen.fade", "{\"clear\":true}");
			hudUntil.remove(handler.getPlayer().getUuid());
			if (perspectiveDebt.remove(handler.getPlayer().getUuid())) {
				PlayerEvents.send(handler.getPlayer(), "camera.perspective", "{\"mode\":\"first\"}");
			}

			Run run = pending.remove(handler.getPlayer().getUuid());
			if (run != null) {
				restore(srv, handler.getPlayer(), run);
				savePending();
				LoreDevLog.info("[tour] {} returned after an unclosed showing",
						handler.getPlayer().getName().getString());
			}
		});
	}


	private static final Path INITIATES = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("tour-initiates.json");

	private void save() {
		savePending();
	}

	private synchronized void saveInitiates() {
		JsonArray array = new JsonArray();
		initiates.forEach((uuid, name) -> {
			JsonObject o = new JsonObject();
			o.addProperty("uuid", uuid.toString());
			o.addProperty("name", name);
			array.add(o);
		});
		try {
			Files.createDirectories(INITIATES.getParent());
			Path tmp = INITIATES.resolveSibling("tour-initiates.json.tmp");
			Files.writeString(tmp, array.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, INITIATES, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to write tour-initiates.json: {}", e.toString());
		}
	}

	private void loadInitiates() {
		if (!Files.exists(INITIATES)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(INITIATES, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("tour-initiates.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				initiates.put(UUID.fromString(o.get("uuid").getAsString()),
						o.has("name") ? o.get("name").getAsString() : "");
			}
			InstantLore.LOGGER.info("Tour initiates loaded: {}", initiates.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read tour-initiates.json: {}", e.toString());
		}
	}

	public List<String> initiates() {
		List<String> out = new ArrayList<>();
		initiates.forEach((uuid, name) -> out.add(name.isBlank() ? uuid.toString() : name));
		java.util.Collections.sort(out);
		return out;
	}

	public boolean setInitiate(UUID uuid, String name, boolean member) {
		boolean changed = member
				? initiates.put(uuid, name) == null
				: initiates.remove(uuid) != null;
		if (changed) {
			saveInitiates();
		}
		return changed;
	}

	private synchronized void savePending() {
		JsonArray array = new JsonArray();
		List<Run> all = new ArrayList<>(pending.values());
		all.addAll(running.values());
		for (Run run : all) {
			JsonObject o = new JsonObject();
			o.addProperty("uuid", run.player.toString());
			o.addProperty("dimension", run.backDimension);
			o.addProperty("x", run.backX);
			o.addProperty("y", run.backY);
			o.addProperty("z", run.backZ);
			o.addProperty("yaw", run.backYaw);
			o.addProperty("pitch", run.backPitch);
			o.addProperty("mode", run.backMode == null ? "SURVIVAL" : run.backMode.name());
			array.add(o);
		}
		try {
			Files.createDirectories(STORAGE.getParent());
			Path tmp = STORAGE.resolveSibling("tour.json.tmp");
			Files.writeString(tmp, array.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, STORAGE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to write tour.json: {}", e.toString());
		}
	}

	private void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("tour.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				Run run = new Run();
				run.player = UUID.fromString(o.get("uuid").getAsString());
				run.backDimension = o.get("dimension").getAsString();
				run.backX = o.get("x").getAsDouble();
				run.backY = o.get("y").getAsDouble();
				run.backZ = o.get("z").getAsDouble();
				run.backYaw = o.get("yaw").getAsFloat();
				run.backPitch = o.get("pitch").getAsFloat();
				run.backMode = modeOf(o.has("mode") ? o.get("mode").getAsString() : "SURVIVAL");
				pending.put(run.player, run);
			}
			if (!pending.isEmpty()) {
				InstantLore.LOGGER.info("Unclosed tour showings: {}", pending.size());
			}
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read tour.json: {}", e.toString());
		}
	}


	private double secondsOf(Stop stop) {
		return stop.seconds() > 0 ? stop.seconds() : stopSeconds;
	}

	private boolean visibleBody(Run run, Stop stop) {
		if (!stop.body().isBlank()) {
			return "player".equals(stop.body());
		}
		Tour tour = tours.get(run.tour);
		return tour != null && !tour.moveBody();
	}

	private String perspectiveOf(Run run) {
		Tour tour = tours.get(run.tour);
		return tour == null ? "" : tour.perspective();
	}

	private String hudOf(Run run) {
		Tour tour = tours.get(run.tour);
		return tour == null ? "" : tour.hud();
	}

	private String firstTour() {
		return tours.isEmpty() ? "" : tours.keySet().iterator().next();
	}

	private List<Stop> stopsOf(Run run) {
		Tour tour = tours.get(run.tour);
		return tour == null ? List.of() : tour.stops();
	}

	private int totalTicks(Run run) {
		double sum = 0;
		for (Stop stop : stopsOf(run)) {
			sum += secondsOf(stop);
		}
		return (int) Math.round(sum * TPS);
	}

	private static GameMode modeOf(String name) {
		for (GameMode mode : GameMode.values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return GameMode.SURVIVAL;
	}

	private static String dimensionOf(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static ServerWorld worldOf(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
	}

	private static double readDouble(JsonObject config, String key, double fallback) {
		try {
			return config.has(key) ? config.get(key).getAsDouble() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
