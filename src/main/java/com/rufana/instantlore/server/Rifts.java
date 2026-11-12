package com.rufana.instantlore.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.module.RitualDagger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Rifts {
	private static final double ENTER_RADIUS = 1.4;

	private static final double EMERGE_DIST = 2.6;

	private static final String RIFT_FADE =
			"{\"color\":\"#FF050505\",\"in\":0.15,\"hold\":1.2,\"out\":1.5}";

	private static final int FADE_LEAD_TICKS = 5;

	private static long tick;

	private record Pending(RegistryKey<World> world, Vec3d exit, float yaw, float pitch,
			long fireTick, String riftId, String partnerId) {
	}

	private static final Map<UUID, Pending> PENDING_TP = new ConcurrentHashMap<>();

	private static final long COOLDOWN_MS = 2000;
	private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

	private static final long GATE_MSG_COOLDOWN_MS = 5000;
	private static final Map<UUID, Long> GATE_MSG_COOLDOWN = new ConcurrentHashMap<>();

	private static final double VISUAL_HALF_HEIGHT = 1.3;

	private static final List<Rift> RIFTS = new CopyOnWriteArrayList<>();

	private static MinecraftServer server;

	private static final java.util.concurrent.atomic.AtomicInteger SEQ =
			new java.util.concurrent.atomic.AtomicInteger();

	private static final Path STORAGE = FabricLoader.getInstance()
			.getConfigDir().resolve("instantlore").resolve("rifts.json");

	private record Rift(String id, RegistryKey<World> world, Vec3d pos, UUID owner,
			String partner) {
		boolean functional() {
			return partner != null;
		}
	}

	private static Rift byId(String id) {
		for (Rift rift : RIFTS) {
			if (rift.id().equals(id)) {
				return rift;
			}
		}
		return null;
	}

	private static String nextId() {
		return "r" + System.currentTimeMillis() + "-" + SEQ.incrementAndGet();
	}

	private Rifts() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Rifts::onTick);

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, srv) -> sendWorldRifts(handler.getPlayer()));
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
				.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) -> {
					PlayerEvents.send(player, "rift.clear", "{}");
					sendWorldRifts(player);
				});
	}

	public static String openMarker(RegistryKey<World> world, Vec3d pos) {
		Rift rift = new Rift(nextId(), world, pos, null, null);
		RIFTS.add(rift);
		save();
		broadcastSet(rift);
		return rift.id();
	}

	public static String openPair(RegistryKey<World> worldA, Vec3d posA,
			RegistryKey<World> worldB, Vec3d posB, UUID owner) {
		String idA = nextId();
		String idB = nextId();
		Rift a = new Rift(idA, worldA, posA, owner, idB);
		Rift b = new Rift(idB, worldB, posB, owner, idA);
		RIFTS.add(a);
		RIFTS.add(b);
		save();
		broadcastSet(a);
		broadcastSet(b);
		return idA;
	}

	public static boolean close(String id) {
		Rift rift = byId(id);
		if (rift == null) {
			return false;
		}
		RIFTS.remove(rift);
		broadcastRemove(rift.id());

		if (rift.partner() != null) {
			Rift partner = byId(rift.partner());
			if (partner != null) {
				RIFTS.remove(partner);
				broadcastRemove(partner.id());
			}
		}
		save();
		return true;
	}

	public record RiftEnd(String id, String where, RegistryKey<World> world, Vec3d pos) {}

	public record RiftLine(RiftEnd a, RiftEnd b, double distance) {}

	private static String dimCode(Identifier world) {
		return switch (world.toString()) {
			case "minecraft:overworld" -> "O";
			case "minecraft:the_nether" -> "N";
			case "minecraft:the_end" -> "E";
			case "rufana:limbo" -> "L";
			default -> world.toString();
		};
	}

	private static RiftEnd endOf(Rift rift) {
		return new RiftEnd(rift.id(), String.format(java.util.Locale.ROOT, "%s %d %d %d",
				dimCode(rift.world().getValue()), Math.round(rift.pos().x),
				Math.round(rift.pos().y), Math.round(rift.pos().z)),
				rift.world(), rift.pos());
	}

	public static List<RiftLine> describe(RegistryKey<World> from, Vec3d origin) {
		List<RiftLine> out = new java.util.ArrayList<>();
		java.util.Set<String> shown = new java.util.HashSet<>();
		for (Rift rift : RIFTS) {
			if (shown.contains(rift.id())) {
				continue;
			}
			RiftEnd a = endOf(rift);
			RiftEnd b = null;
			if (rift.functional()) {
				Rift partner = byId(rift.partner());
				if (partner != null) {
					shown.add(partner.id());
					b = endOf(partner);
				}
			}
			double distance = Math.min(distanceTo(a, from, origin), distanceTo(b, from, origin));
			out.add(new RiftLine(a, b, distance == Double.MAX_VALUE ? -1 : distance));
		}
		out.sort(java.util.Comparator
				.comparingDouble((RiftLine line) -> line.distance() < 0 ? Double.MAX_VALUE : line.distance())
				.thenComparing(line -> line.a().id()));
		return out;
	}

	private static double distanceTo(RiftEnd end, RegistryKey<World> from, Vec3d origin) {
		if (end == null || from == null || !end.world().equals(from)) {
			return Double.MAX_VALUE;
		}
		return end.pos().distanceTo(origin);
	}

	private static void onTick(MinecraftServer srv) {
		tick++;

		if (!PENDING_TP.isEmpty()) {
			firePending(srv);
		}

		if (RIFTS.isEmpty()) {
			return;
		}

		for (Rift rift : RIFTS) {
			if (rift.functional()) {
				tryExit(srv, rift);
			}
		}
	}

	private static void firePending(MinecraftServer srv) {
		for (UUID uuid : PENDING_TP.keySet()) {
			Pending p = PENDING_TP.get(uuid);
			if (p == null || tick < p.fireTick()) {
				continue;
			}
			PENDING_TP.remove(uuid);

			ServerPlayerEntity player = srv.getPlayerManager().getPlayer(uuid);
			if (player == null) {
				continue;
			}
			ServerWorld dest = srv.getWorld(p.world());
			if (dest == null) {
				InstantLore.LOGGER.error(
						"Deferred rift transition {}: world {} is gone",
						p.riftId(), p.world().getValue());
				continue;
			}

			player.teleportTo(new TeleportTarget(dest, p.exit(),
					Vec3d.ZERO, p.yaw(), p.pitch(), TeleportTarget.NO_OP));

			LoreDevLog.info("{} passed through rift {} → paired {} in {} @ {}",
					player.getName().getString(), p.riftId(), p.partnerId(),
					p.world().getValue(), p.exit());
		}
	}

	private static void broadcastSet(Rift rift) {
		if (server == null) {
			return;
		}
		String json = riftJson(rift);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.getEntityWorld().getRegistryKey().equals(rift.world())) {
				PlayerEvents.send(p, "rift.set", json);
			}
		}
	}

	private static void broadcastRemove(String id) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerEvents.send(p, "rift.remove", "{\"id\":\"" + id + "\"}");
		}
	}

	private static void sendWorldRifts(ServerPlayerEntity player) {
		RegistryKey<World> world = player.getEntityWorld().getRegistryKey();
		for (Rift rift : RIFTS) {
			if (rift.world().equals(world)) {
				PlayerEvents.send(player, "rift.set", riftJson(rift));
			}
		}
	}

	private static String riftJson(Rift rift) {
		return "{\"id\":\"" + rift.id() + "\",\"x\":" + rift.pos().x
				+ ",\"y\":" + (rift.pos().y + VISUAL_HALF_HEIGHT) + ",\"z\":" + rift.pos().z + "}";
	}

	private static void tryExit(MinecraftServer server, Rift rift) {
		ServerWorld world = server.getWorld(rift.world());
		if (world == null) {
			return;
		}

		Rift partner = byId(rift.partner());
		if (partner == null) {
			InstantLore.LOGGER.error(
					"Rift {} lost its paired end {} - transition impossible",
					rift.id(), rift.partner());
			return;
		}

		ServerWorld dest = server.getWorld(partner.world());
		if (dest == null) {
			InstantLore.LOGGER.error(
					"Paired end of rift {} leads to a nonexistent world {}",
					rift.id(), partner.world().getValue());
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			Long until = COOLDOWN.get(player.getUuid());
			if (until != null && System.currentTimeMillis() < until) {
				continue;
			}

			if (player.squaredDistanceTo(rift.pos().x, rift.pos().y, rift.pos().z)
					> ENTER_RADIUS * ENTER_RADIUS) {
				continue;
			}

			if (partner.world().equals(LimboTransfer.LIMBO_KEY)
					&& !ClientVersions.meetsMinimum(player.getUuid(),
							RitualDagger.MIN_MAJOR, RitualDagger.MIN_MINOR, RitualDagger.MIN_PATCH)) {
				notifyStaleClient(player);
				continue;
			}

			Vec3d entrySide =
					new Vec3d(player.getX() - rift.pos().x, 0, player.getZ() - rift.pos().z);
			if (entrySide.lengthSquared() < 0.01) {
				Vec3d look = player.getRotationVector();
				entrySide = new Vec3d(-look.x, 0, -look.z);
			}
			entrySide = entrySide.normalize();

			Vec3d exit = emergence(dest, partner.pos(), entrySide);

			COOLDOWN.put(player.getUuid(), System.currentTimeMillis() + COOLDOWN_MS);
			PlayerEvents.send(player, "screen.fade", RIFT_FADE);
			PENDING_TP.put(player.getUuid(), new Pending(partner.world(), exit,
					player.getYaw(), player.getPitch(), tick + FADE_LEAD_TICKS,
					rift.id(), partner.id()));
		}
	}

	private static void notifyStaleClient(ServerPlayerEntity player) {
		long now = System.currentTimeMillis();
		Long until = GATE_MSG_COOLDOWN.get(player.getUuid());
		if (until != null && now < until) {
			return;
		}
		GATE_MSG_COOLDOWN.put(player.getUuid(), now + GATE_MSG_COOLDOWN_MS);
		player.sendMessage(Text.literal("Разлом не принимает — твой мод слишком стар. Обнови до "
				+ RitualDagger.MIN_MAJOR + "." + RitualDagger.MIN_MINOR + "." + RitualDagger.MIN_PATCH
				+ ", иначе за разломом будет лишь пустота.").formatted(Formatting.DARK_PURPLE), false);
	}

	private static Vec3d emergence(ServerWorld world, Vec3d riftPos, Vec3d entrySide) {
		Vec3d primary = riftPos.subtract(entrySide.multiply(EMERGE_DIST));
		if (isClear(world, primary)) {
			return primary;
		}
		for (int i = 1; i < 8; i++) {
			double a = Math.PI * i / 4.0;
			Vec3d rotated = new Vec3d(
					entrySide.x * Math.cos(a) - entrySide.z * Math.sin(a), 0,
					entrySide.x * Math.sin(a) + entrySide.z * Math.cos(a));
			Vec3d candidate = riftPos.subtract(rotated.multiply(EMERGE_DIST));
			if (isClear(world, candidate)) {
				return candidate;
			}
		}
		return riftPos;
	}

	private static boolean isClear(ServerWorld world, Vec3d feet) {
		net.minecraft.util.math.BlockPos p = net.minecraft.util.math.BlockPos.ofFloored(feet);
		return world.getBlockState(p).getCollisionShape(world, p).isEmpty()
				&& world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty();
	}

	public static void registerStorage() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			server = srv;
			load();
		});
	}

	private static RegistryKey<World> worldKey(String id) {
		return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(id));
	}

	private static void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}

		try {
			JsonElement parsed = JsonParser.parseString(
					Files.readString(STORAGE, StandardCharsets.UTF_8));

			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("rifts.json - expected an array, found: {}", parsed);
				return;
			}

			RIFTS.clear();

			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				String id = o.has("id") ? o.get("id").getAsString() : nextId();
				RegistryKey<World> world = worldKey(o.get("world").getAsString());
				Vec3d pos = new Vec3d(o.get("x").getAsDouble(),
						o.get("y").getAsDouble(), o.get("z").getAsDouble());

				UUID owner = o.has("owner")
						? UUID.fromString(o.get("owner").getAsString()) : null;
				String partner = o.has("partner") ? o.get("partner").getAsString() : null;

				RIFTS.add(new Rift(id, world, pos, owner, partner));
			}

			InstantLore.LOGGER.info("Rifts loaded: {}", RIFTS.size());
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read rifts.json: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray array = new JsonArray();

			for (Rift rift : RIFTS) {
				JsonObject o = new JsonObject();
				o.addProperty("id", rift.id());
				o.addProperty("world", rift.world().getValue().toString());
				o.addProperty("x", rift.pos().x);
				o.addProperty("y", rift.pos().y);
				o.addProperty("z", rift.pos().z);

				if (rift.partner() != null) {
					o.addProperty("partner", rift.partner());
				}
				if (rift.owner() != null) {
					o.addProperty("owner", rift.owner().toString());
				}

				array.add(o);
			}

			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, array.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save rifts.json: {}", e.toString());
		}
	}
}
