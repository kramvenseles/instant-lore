package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LimboTransfer;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.OfferingEvents;
import com.rufana.instantlore.server.PlayerEvents;
import com.rufana.instantlore.server.Rifts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RiteModule implements LoreModule {

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("rite.json");

	private record Spot(String dimension, BlockPos pos) {
		String id() {
			return dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
		}
	}

	private static final class Member {
		Spot spot;
		boolean secret;

		int stormRadius;
		int stormHeight = 1;
		int stormPeriod = 1;
	}

	private static final class Rite {
		String name;
		final List<Member> members = new ArrayList<>();

		String gateDimension;
		BlockPos gate;

		String completeEvent = "rite.complete";
		String secretEvent = "rite.secret";

		int quorum;

		boolean fired;
		boolean secretFired;

		String riftId = "";

		List<Member> role(boolean secret) {
			List<Member> out = new ArrayList<>();
			for (Member m : members) {
				if (m.secret == secret) {
					out.add(m);
				}
			}
			return out;
		}

		Member at(Spot spot) {
			for (Member m : members) {
				if (m.spot.id().equals(spot.id())) {
					return m;
				}
			}
			return null;
		}
	}

	private final OfferingModule offering;
	private final Map<String, Rite> rites = new ConcurrentHashMap<>();

	private int maxRites = 64;
	private int maxMembers = 16;
	private int maxStormRadius = 128;
	private int notifyRadius = 48;

	private int sinceCheck;
	private long ticks;

	public RiteModule(OfferingModule offering) {
		this.offering = offering;
	}

	@Override
	public String id() {
		return "rite";
	}

	@Override
	public String title() {
		return "Обряды";
	}

	@Override
	public void configure(JsonObject config) {
		maxRites = clamp(readInt(config, "max_rites", 64), 1, 512);
		maxMembers = clamp(readInt(config, "max_members", 16), 1, 64);
		maxStormRadius = clamp(readInt(config, "max_storm_radius", 128), 1, 256);
		notifyRadius = clamp(readInt(config, "notify_radius", 48), 1, 128);
	}

	@Override
	public String describe() {
		return "обрядов " + rites.size() + "/" + maxRites
				+ ", радиус оповещения " + notifyRadius;
	}

	@Override
	public void onEnable(MinecraftServer server) {
		load();
	}

	public void subscribe() {
		OfferingEvents.subscribe((world, pos, type, title, by, need, have) -> {
			Spot spot = new Spot(dimensionOf(world), pos);
			for (Rite rite : rites.values()) {
				if (rite.at(spot) != null) {
					evaluate(world.getServer(), rite, by);
				}
			}
		});
	}


	@Override
	public void tick(MinecraftServer server) {
		ticks++;
		storm(server);

		if (++sinceCheck < 20) {
			return;
		}
		sinceCheck = 0;
		for (Rite rite : new ArrayList<>(rites.values())) {
			evaluate(server, rite, null);
		}
	}

	private void evaluate(MinecraftServer server, Rite rite, ServerPlayerEntity by) {
		if (!rite.fired && complete(server, rite)) {
			rite.fired = true;
			openGate(server, rite, by);
			fire(server, rite, "core", rite.completeEvent, by);
			save();
		}
		if (!rite.secretFired && full(server, rite.role(true))) {
			rite.secretFired = true;
			fire(server, rite, "secret", rite.secretEvent, by);
			save();
		}
	}

	private boolean complete(MinecraftServer server, Rite rite) {
		if (rite.quorum <= 0) {
			return full(server, rite.role(false));
		}
		return filled(server, rite.members) >= rite.quorum;
	}

	private int filled(MinecraftServer server, List<Member> members) {
		int count = 0;
		for (Member member : members) {
			ServerWorld world = worldOf(server, member.spot.dimension());
			if (world != null && offering.isFull(world, member.spot.pos())) {
				count++;
			}
		}
		return count;
	}

	private boolean full(MinecraftServer server, List<Member> members) {
		if (members.isEmpty()) {
			return false;
		}
		for (Member member : members) {
			ServerWorld world = worldOf(server, member.spot.dimension());
			if (world == null || !offering.isFull(world, member.spot.pos())) {
				return false;
			}
		}
		return true;
	}

	private void openGate(MinecraftServer server, Rite rite, ServerPlayerEntity by) {
		if (rite.gate == null || rite.gateDimension == null) {
			LoreDevLog.info("[rite] {} assembled but gate point is not set - portal not opened", rite.name);
			return;
		}
		ServerWorld world = worldOf(server, rite.gateDimension);
		if (world == null) {
			InstantLore.LOGGER.error("Rite {}: gate world {} not found", rite.name, rite.gateDimension);
			return;
		}

		Vec3d here = new Vec3d(rite.gate.getX() + 0.5, rite.gate.getY(), rite.gate.getZ() + 0.5);
		rite.riftId = Rifts.openPair(world.getRegistryKey(), here,
				LimboTransfer.LIMBO_KEY, LimboTransfer.limboMirror(rite.gate),
				by == null ? null : by.getUuid());

		world.playSound(null, rite.gate, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.0f, 0.8f);
		LoreDevLog.info("[rite] {} - gate opened @ {} (rift {})",
				rite.name, rite.gate.toShortString(), rite.riftId);
	}

	private void fire(MinecraftServer server, Rite rite, String kind, String type,
			ServerPlayerEntity by) {
		BlockPos where = rite.gate != null ? rite.gate
				: (rite.members.isEmpty() ? BlockPos.ORIGIN : rite.members.get(0).spot.pos());

		OfferingEvents.fireRite(server, rite.name, kind, type, rite.gateDimension, rite.gate, by);

		String json = "{\"rite\":\"" + escape(rite.name) + "\""
				+ ",\"kind\":\"" + kind + "\""
				+ ",\"x\":" + where.getX() + ",\"y\":" + where.getY() + ",\"z\":" + where.getZ()
				+ ",\"by\":\"" + (by == null ? "" : escape(by.getName().getString())) + "\"}";

		ServerWorld world = rite.gateDimension == null ? null : worldOf(server, rite.gateDimension);
		if (world == null && !rite.members.isEmpty()) {
			world = worldOf(server, rite.members.get(0).spot.dimension());
		}
		if (world != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.squaredDistanceTo(where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5)
						<= (double) notifyRadius * notifyRadius) {
					PlayerEvents.send(player, type, json);
				}
			}
		}

		LoreDevLog.info("[rite] {} ({}) → event {}{}", rite.name, kind, type,
				by == null ? "" : ", completed " + by.getName().getString());
	}


	private void storm(MinecraftServer server) {
		for (Rite rite : rites.values()) {
			for (Member member : rite.members) {
				if (member.stormRadius <= 0 || ticks % member.stormPeriod != 0) {
					continue;
				}
				ServerWorld world = worldOf(server, member.spot.dimension());
				if (world == null) {
					continue;
				}
				if (!world.isChunkLoaded(ChunkPos.toLong(member.spot.pos()))) {
					continue;
				}
				if (offering.isFull(world, member.spot.pos())) {
					continue;
				}
				if (!anyoneNear(world, member)) {
					continue;
				}
				strike(world, member);
			}
		}
	}

	private boolean anyoneNear(ServerWorld world, Member member) {
		double r = (double) member.stormRadius * member.stormRadius;
		BlockPos pos = member.spot.pos();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r) {
				return true;
			}
		}
		return false;
	}

	private void strike(ServerWorld world, Member member) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		BlockPos pos = member.spot.pos();
		bolt.setPosition(pos.getX() + 0.5, pos.getY() + member.stormHeight, pos.getZ() + 0.5);
		bolt.setCosmetic(true);
		world.spawnEntity(bolt);
	}


	public String add(ServerWorld world, BlockPos pos, String name, boolean secret) {
		if (!offering.exists(world, pos)) {
			return "в этой точке нет настроенного жертвенника";
		}
		Rite rite = rites.get(name);
		if (rite == null) {
			if (rites.size() >= maxRites) {
				return "обрядов уже " + maxRites + " — потолок";
			}
			rite = new Rite();
			rite.name = name;
			rites.put(name, rite);
		}
		Spot spot = new Spot(dimensionOf(world), pos);
		Member existing = rite.at(spot);
		if (existing != null) {
			existing.secret = secret;
			save();
			return null;
		}
		if (rite.members.size() >= maxMembers) {
			return "в обряде уже " + maxMembers + " жертвенников — потолок";
		}
		Member member = new Member();
		member.spot = spot;
		member.secret = secret;
		rite.members.add(member);
		save();
		return null;
	}

	public String remove(ServerWorld world, BlockPos pos, String name) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return "нет такого обряда";
		}
		Member member = rite.at(new Spot(dimensionOf(world), pos));
		if (member == null) {
			return "этот жертвенник не в обряде " + name;
		}
		rite.members.remove(member);
		save();
		return null;
	}

	public String setGate(ServerWorld world, BlockPos pos, String name) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return "нет такого обряда";
		}
		rite.gateDimension = dimensionOf(world);
		rite.gate = pos;
		save();
		return null;
	}

	public String setQuorum(String name, int count) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return "нет такого обряда";
		}
		if (count < 0 || count > maxMembers) {
			return "кворум — от 0 до " + maxMembers;
		}
		rite.quorum = count;
		save();
		return null;
	}

	public int quorumOf(String name) {
		Rite rite = rites.get(name);
		return rite == null ? -1 : rite.quorum;
	}

	public int filledOf(MinecraftServer server, String name) {
		Rite rite = rites.get(name);
		return rite == null ? -1 : filled(server, rite.members);
	}

	public int sizeOf(String name) {
		Rite rite = rites.get(name);
		return rite == null ? -1 : rite.members.size();
	}

	public String setEvent(String name, boolean secret, String type) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return "нет такого обряда";
		}
		if (secret) {
			rite.secretEvent = type;
		} else {
			rite.completeEvent = type;
		}
		save();
		return null;
	}

	public String setStorm(ServerWorld world, BlockPos pos, int radius, int height, int period) {
		Spot spot = new Spot(dimensionOf(world), pos);
		Member found = null;
		for (Rite rite : rites.values()) {
			Member member = rite.at(spot);
			if (member != null) {
				found = member;
				break;
			}
		}
		if (found == null) {
			return "этот жертвенник не входит ни в один обряд";
		}
		found.stormRadius = clamp(radius, 0, maxStormRadius);
		found.stormHeight = clamp(height, -16, 64);
		found.stormPeriod = clamp(period, 1, 200);
		save();
		return null;
	}

	public String reset(MinecraftServer server, String name) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return "нет такого обряда";
		}
		if (!rite.riftId.isBlank()) {
			Rifts.close(rite.riftId);
			rite.riftId = "";
		}
		rite.fired = false;
		rite.secretFired = false;
		save();
		return null;
	}

	public boolean drop(MinecraftServer server, String name) {
		Rite rite = rites.remove(name);
		if (rite == null) {
			return false;
		}
		if (!rite.riftId.isBlank()) {
			Rifts.close(rite.riftId);
		}
		save();
		return true;
	}

	public List<String> info(MinecraftServer server, String name) {
		Rite rite = rites.get(name);
		if (rite == null) {
			return null;
		}
		List<String> out = new ArrayList<>();
		out.add(rite.name
				+ (rite.fired ? " — свершён" : " — идёт")
				+ (rite.secretFired ? ", тайное открыто" : ""));
		out.add("нужно: " + (rite.quorum <= 0
				? "все основные (" + rite.role(false).size() + ")"
				: rite.quorum + " любых из " + rite.members.size())
				+ " — набрано " + filled(server, rite.members));
		out.add("врата: " + (rite.gate == null ? "не заданы"
				: rite.gateDimension + " " + rite.gate.toShortString())
				+ (rite.riftId.isBlank() ? "" : " (разлом " + rite.riftId + ")"));
		out.add("события: " + rite.completeEvent + " / " + rite.secretEvent);

		for (Member member : rite.members) {
			ServerWorld world = worldOf(server, member.spot.dimension());
			String count = world == null ? "мир не загружен" : offering.summaryOf(world, member.spot.pos());
			out.add((member.secret ? "тайный " : "основной ")
					+ member.spot.pos().toShortString()
					+ " — " + (count == null ? "жертвенник снят" : count)
					+ (member.stormRadius > 0
							? " · зов " + member.stormRadius + "б, +" + member.stormHeight
									+ ", раз в " + member.stormPeriod + " т."
							: ""));
		}
		return out;
	}

	public List<String> list() {
		List<String> out = new ArrayList<>();
		for (Rite rite : rites.values()) {
			out.add(rite.name + " — основных " + rite.role(false).size()
					+ ", тайных " + rite.role(true).size()
					+ (rite.quorum > 0 ? ", кворум " + rite.quorum : "")
					+ (rite.fired ? ", свершён" : "")
					+ (rite.gate == null ? ", врат нет" : ""));
		}
		return out;
	}


	private synchronized void save() {
		JsonArray array = new JsonArray();
		for (Rite rite : rites.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", rite.name);
			o.addProperty("quorum", rite.quorum);
			o.addProperty("complete_event", rite.completeEvent);
			o.addProperty("secret_event", rite.secretEvent);
			o.addProperty("fired", rite.fired);
			o.addProperty("secret_fired", rite.secretFired);
			o.addProperty("rift", rite.riftId);
			if (rite.gate != null && rite.gateDimension != null) {
				o.addProperty("gate_dimension", rite.gateDimension);
				o.addProperty("gate_x", rite.gate.getX());
				o.addProperty("gate_y", rite.gate.getY());
				o.addProperty("gate_z", rite.gate.getZ());
			}
			JsonArray members = new JsonArray();
			for (Member member : rite.members) {
				JsonObject m = new JsonObject();
				m.addProperty("dimension", member.spot.dimension());
				m.addProperty("x", member.spot.pos().getX());
				m.addProperty("y", member.spot.pos().getY());
				m.addProperty("z", member.spot.pos().getZ());
				m.addProperty("secret", member.secret);
				m.addProperty("storm_radius", member.stormRadius);
				m.addProperty("storm_height", member.stormHeight);
				m.addProperty("storm_period", member.stormPeriod);
				members.add(m);
			}
			o.add("members", members);
			array.add(o);
		}

		try {
			Files.createDirectories(STORAGE.getParent());
			Path tmp = STORAGE.resolveSibling("rite.json.tmp");
			Files.writeString(tmp, array.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, STORAGE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to write rite.json: {}", e.toString());
		}
	}

	private void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("rite.json - expected an array, found: {}", parsed);
				return;
			}
			Map<String, Rite> loaded = new LinkedHashMap<>();
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				Rite rite = new Rite();
				rite.name = o.get("name").getAsString();
				rite.quorum = o.has("quorum") ? o.get("quorum").getAsInt() : 0;
				rite.completeEvent = o.has("complete_event")
						? o.get("complete_event").getAsString() : "rite.complete";
				rite.secretEvent = o.has("secret_event")
						? o.get("secret_event").getAsString() : "rite.secret";
				rite.fired = o.has("fired") && o.get("fired").getAsBoolean();
				rite.secretFired = o.has("secret_fired") && o.get("secret_fired").getAsBoolean();
				rite.riftId = o.has("rift") ? o.get("rift").getAsString() : "";
				if (o.has("gate_dimension")) {
					rite.gateDimension = o.get("gate_dimension").getAsString();
					rite.gate = new BlockPos(o.get("gate_x").getAsInt(),
							o.get("gate_y").getAsInt(), o.get("gate_z").getAsInt());
				}
				if (o.has("members")) {
					for (JsonElement e : o.getAsJsonArray("members")) {
						JsonObject m = e.getAsJsonObject();
						Member member = new Member();
						member.spot = new Spot(m.get("dimension").getAsString(),
								new BlockPos(m.get("x").getAsInt(), m.get("y").getAsInt(), m.get("z").getAsInt()));
						member.secret = m.has("secret") && m.get("secret").getAsBoolean();
						member.stormRadius = m.has("storm_radius") ? m.get("storm_radius").getAsInt() : 0;
						member.stormHeight = m.has("storm_height") ? m.get("storm_height").getAsInt() : 1;
						member.stormPeriod = m.has("storm_period")
								? Math.max(1, m.get("storm_period").getAsInt()) : 1;
						rite.members.add(member);
					}
				}
				loaded.put(rite.name, rite);
			}
			rites.clear();
			rites.putAll(loaded);
			LoreDevLog.info("[rite] entries loaded: {}", rites.size());
		} catch (Throwable e) {
			InstantLore.LOGGER.error("rite.json not read: {}", e.toString());
		}
	}


	private static String dimensionOf(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static ServerWorld worldOf(MinecraftServer server, String dimension) {
		for (ServerWorld world : server.getWorlds()) {
			if (dimensionOf(world).equals(dimension)) {
				return world;
			}
		}
		return null;
	}

	private static int readInt(JsonObject config, String key, int fallback) {
		try {
			return config.has(key) ? config.get(key).getAsInt() : fallback;
		} catch (Throwable e) {
			return fallback;
		}
	}

	private static int clamp(int value, int lo, int hi) {
		return Math.max(lo, Math.min(hi, value));
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
