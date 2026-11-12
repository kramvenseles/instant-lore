package com.rufana.instantlore.server.module;

import com.google.gson.JsonObject;
import com.rufana.instantlore.content.Coins;
import com.rufana.instantlore.content.LoreItemPool;
import com.rufana.instantlore.server.LimboTransfer;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.PlayerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HarvestModule implements LoreModule {

	private static final Set<UUID> ASCENDING = ConcurrentHashMap.newKeySet();

	private static final double DEFAULT_RISE_PER_TICK = 0.35;

	private static final double DEFAULT_HEIGHT = 24.0;

	private static final int DEFAULT_RADIUS = 10;
	private static final int DEFAULT_COIN = 1;
	private static final int DEFAULT_MAX_GROUPS = 8;
	private static final String DEFAULT_EVENT = "limbo.harvest";
	private static final int DEFAULT_NOTIFY_RADIUS = 48;

	private static final double DEFAULT_STREAM_DENSITY = 4.0;

	private static final double DEFAULT_STREAM_WIDTH = 1.6;

	private static final int DEFAULT_STREAM_COLOR = 0x8FD8FF;

	private static final int STREAM_MAX_POINTS = 64;

	private static final double STREAM_JITTER = 0.02;

	private static final int STREAM_SOULS = 3;
	private static final int STREAM_PERIOD = 16;

	private static final Set<UUID> LINKED = ConcurrentHashMap.newKeySet();

	private static final int MAX_TICKS = 400;

	private static final List<EntityType<?>> NEEDED = List.of(
			EntityType.SNOW_GOLEM, EntityType.IRON_GOLEM, EntityType.WITHER);

	private int radius = DEFAULT_RADIUS;
	private int coin = DEFAULT_COIN;
	private double risePerTick = DEFAULT_RISE_PER_TICK;
	private double height = DEFAULT_HEIGHT;
	private int maxGroups = DEFAULT_MAX_GROUPS;
	private String event = DEFAULT_EVENT;
	private int notifyRadius = DEFAULT_NOTIFY_RADIUS;

	private int pioneerSlot = -1;

	private int pioneerCoins = 2;
	private boolean streams = true;
	private double streamDensity = DEFAULT_STREAM_DENSITY;
	private double streamWidth = DEFAULT_STREAM_WIDTH;
	private int streamColor = DEFAULT_STREAM_COLOR;

	private final List<Ascension> groups = new ArrayList<>();

	private static final class Ascension {
		final List<UUID> members = new ArrayList<>(4);
		final Vec3d anchor;
		final double topY;
		int ticks;

		final UUID author;

		Ascension(Vec3d anchor, double topY, UUID author) {
			this.anchor = anchor;
			this.topY = topY;
			this.author = author;
		}
	}

	public static boolean isAscending(UUID uuid) {
		return ASCENDING.contains(uuid);
	}

	@Override
	public String id() {
		return "harvest";
	}

	@Override
	public String title() {
		return "Жатва душ";
	}

	@Override
	public void configure(JsonObject config) {
		radius = clampInt(config, "radius", DEFAULT_RADIUS, 1, 64);
		coin = clampInt(config, "coin", DEFAULT_COIN, 1, 1_000_000);
		maxGroups = clampInt(config, "max_groups", DEFAULT_MAX_GROUPS, 1, 64);
		notifyRadius = clampInt(config, "notify_radius", DEFAULT_NOTIFY_RADIUS, 0, 256);
		risePerTick = clampDouble(config, "rise_per_tick", DEFAULT_RISE_PER_TICK, 0.05, 4.0);
		height = clampDouble(config, "height", DEFAULT_HEIGHT, 4.0, 128.0);
		streams = !config.has("streams") || !config.get("streams").isJsonPrimitive()
				|| config.get("streams").getAsBoolean();
		streamDensity = clampDouble(config, "stream_density", DEFAULT_STREAM_DENSITY, 0.5, 12.0);
		streamWidth = clampDouble(config, "stream_width", DEFAULT_STREAM_WIDTH, 0.2, 4.0);
		streamColor = colorOf(config, "stream_color", DEFAULT_STREAM_COLOR);
		pioneerSlot = clampInt(config, "pioneer_slot", -1, -1, 4095);
		pioneerCoins = clampInt(config, "pioneer_coins", 2, 1, 64);
		if (config.has("event") && config.get("event").isJsonPrimitive()) {
			String value = config.get("event").getAsString().trim();
			event = value.isEmpty() ? DEFAULT_EVENT : value;
		} else {
			event = DEFAULT_EVENT;
		}
	}

	@Override
	public String describe() {
		return "радиус " + radius + ", монета " + coin + ", подъём " + risePerTick
				+ " бл/тик на " + height + " бл, событие " + event
				+ ", связь " + (streams
						? "вкл (плотность " + streamDensity + ", толщина " + streamWidth
								+ ", цвет #" + String.format("%06X", streamColor) + ")"
						: "выкл");
	}

	@Override
	public void onDisable(MinecraftServer server) {
		groups.clear();
		ASCENDING.clear();
		LINKED.clear();
	}

	@Override
	public void tick(MinecraftServer server) {
		ServerWorld limbo = server.getWorld(LimboTransfer.LIMBO_KEY);
		if (limbo == null) {
			return;
		}
		advance(limbo);
		Scan scan = scan(limbo);
		if (groups.size() < maxGroups) {
			seek(limbo, scan);
		}
		if (streams) {
			weave(limbo, scan);
		} else {
			LINKED.clear();
		}
	}

	private static final class Scan {
		final List<Entity> chickens = new ArrayList<>();
		final List<Entity> golems = new ArrayList<>();
	}

	private Scan scan(ServerWorld world) {
		Scan scan = new Scan();
		for (Entity entity : world.iterateEntities()) {
			if (entity == null || entity instanceof PlayerEntity
					|| ASCENDING.contains(entity.getUuid())) {
				continue;
			}
			if (entity.getType() == EntityType.CHICKEN) {
				scan.chickens.add(entity);
			} else if (NEEDED.contains(entity.getType())) {
				scan.golems.add(entity);
			}
		}
		return scan;
	}

	private void advance(ServerWorld world) {
		for (Iterator<Ascension> it = groups.iterator(); it.hasNext(); ) {
			Ascension group = it.next();
			group.ticks++;

			for (Iterator<UUID> m = group.members.iterator(); m.hasNext(); ) {
				Entity entity = world.getEntity(m.next());
				if (entity == null || entity.isRemoved()) {
					m.remove();
					continue;
				}
				entity.setPosition(entity.getX(), entity.getY() + risePerTick, entity.getZ());
				trail(world, entity);
				if (entity.getY() >= group.topY || group.ticks >= MAX_TICKS) {
					dissolve(world, entity);
					m.remove();
				}
			}

			if (group.members.isEmpty() || group.ticks >= MAX_TICKS) {
				for (UUID uuid : group.members) {
					Entity entity = world.getEntity(uuid);
					if (entity != null) {
						dissolve(world, entity);
					}
					ASCENDING.remove(uuid);
				}
				reward(world, group);
				it.remove();
			}
		}
	}

	private void seek(ServerWorld world, Scan scan) {
		if (scan.chickens.isEmpty() || scan.golems.size() < NEEDED.size()) {
			return;
		}

		for (Entity chicken : scan.chickens) {
			List<Entity> found = around(chicken, scan);
			if (found.size() == NEEDED.size()) {
				List<UUID> members = new ArrayList<>(4);
				for (Entity entity : found) {
					members.add(entity.getUuid());
				}
				members.add(chicken.getUuid());
				begin(world, chicken, members);
				return;
			}
		}
	}

	private List<Entity> around(Entity chicken, Scan scan) {
		double r2 = (double) radius * radius;
		List<Entity> found = new ArrayList<>(NEEDED.size());
		for (EntityType<?> type : NEEDED) {
			for (Entity other : scan.golems) {
				if (other.getType() == type && !ASCENDING.contains(other.getUuid())
						&& other.squaredDistanceTo(chicken) <= r2) {
					found.add(other);
					break;
				}
			}
		}
		return found;
	}

	private void begin(ServerWorld world, Entity anchor, List<UUID> members) {
		UUID author = SummonAuthors.common(members);
		Ascension group = new Ascension(
				new Vec3d(anchor.getX(), anchor.getY(), anchor.getZ()), anchor.getY() + height,
				author);
		for (UUID uuid : members) {
			Entity entity = world.getEntity(uuid);
			if (entity == null) {
				continue;
			}
			if (entity instanceof MobEntity mob) {
				mob.setAiDisabled(true);
			}
			entity.setNoGravity(true);
			entity.noClip = true;
			entity.setInvulnerable(true);
			group.members.add(uuid);
			ASCENDING.add(uuid);
		}
		if (group.members.isEmpty()) {
			return;
		}
		groups.add(group);
		world.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 1.0f, 0.6f);
		LoreDevLog.info("[harvest] the four converged at {} {} {} - heading up (author: {})",
				(int) anchor.getX(), (int) anchor.getY(), (int) anchor.getZ(),
				author == null ? "more than one person" : author);
		SummonAuthors.forget(members);
	}

	private void weave(ServerWorld world, Scan scan) {
		LINKED.clear();

		List<Entity> members = new ArrayList<>(scan.chickens.size() + scan.golems.size());
		members.addAll(scan.chickens);
		members.addAll(scan.golems);

		double r2 = (double) radius * radius;
		for (int i = 0; i < members.size(); i++) {
			for (int j = i + 1; j < members.size(); j++) {
				Entity a = members.get(i);
				Entity b = members.get(j);
				if (a.getType() == b.getType() || a.squaredDistanceTo(b) > r2) {
					continue;
				}
				LINKED.add(a.getUuid());
				LINKED.add(b.getUuid());
				stream(world, a, b);
			}
		}

		for (Ascension group : groups) {
			List<Entity> alive = new ArrayList<>(4);
			for (UUID uuid : group.members) {
				Entity entity = world.getEntity(uuid);
				if (entity != null) {
					alive.add(entity);
				}
			}
			for (int i = 0; i < alive.size(); i++) {
				for (int j = i + 1; j < alive.size(); j++) {
					stream(world, alive.get(i), alive.get(j));
				}
			}
		}
	}

	private void stream(ServerWorld world, Entity a, Entity b) {
		double ax = a.getX();
		double ay = a.getY() + a.getHeight() * 0.5;
		double az = a.getZ();
		double dx = b.getX() - ax;
		double dy = (b.getY() + b.getHeight() * 0.5) - ay;
		double dz = b.getZ() - az;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 0.01) {
			return;
		}

		int points = Math.min(STREAM_MAX_POINTS, Math.max(4, (int) (length * streamDensity)));
		DustParticleEffect dust = new DustParticleEffect(streamColor, (float) streamWidth);
		for (int i = 0; i <= points; i++) {
			double t = (double) i / points;
			world.spawnParticles(dust, ax + dx * t, ay + dy * t, az + dz * t,
					1, STREAM_JITTER, STREAM_JITTER, STREAM_JITTER, 0.0);
		}

		double phase = (world.getTime() % STREAM_PERIOD) / (double) STREAM_PERIOD;
		for (int i = 0; i < STREAM_SOULS; i++) {
			double t = (phase + (double) i / STREAM_SOULS) % 1.0;
			world.spawnParticles(ParticleTypes.SOUL, ax + dx * t, ay + dy * t, az + dz * t,
					1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	public static boolean isLinked(UUID uuid) {
		return LINKED.contains(uuid);
	}

	private void trail(ServerWorld world, Entity entity) {
		world.spawnParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 0.3, entity.getZ(),
				3, 0.25, 0.25, 0.25, 0.02);
	}

	private void dissolve(ServerWorld world, Entity entity) {
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY(),
				entity.getZ(), 40, 0.4, 0.6, 0.4, 0.08);
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.AMBIENT, 1.0f, 0.7f);
		ASCENDING.remove(entity.getUuid());
		entity.discard();
	}

	private void reward(ServerWorld world, Ascension group) {
		Coins.Denom denom = denomFor(coin);
		if (denom == null) {
			LoreDevLog.info("[harvest] coin of denomination {} not declared in items.json - payment not issued", coin);
			return;
		}
		double x = group.anchor.getX();
		double z = group.anchor.getZ();
		int count = pioneerBonus(world, group) ? pioneerCoins : 1;
		ItemEntity drop = new ItemEntity(world, x, group.topY, z,
				new ItemStack(LoreItemPool.get(denom.slot()), count));
		drop.setVelocity(Vec3d.ZERO);
		world.spawnEntity(drop);
		world.playSound(null, x, group.topY, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
				SoundCategory.AMBIENT, 1.0f, 1.2f);

		if (notifyRadius > 0) {
			String json = "{\"x\":" + (int) x + ",\"y\":" + (int) group.topY + ",\"z\":" + (int) z
					+ ",\"coin\":" + denom.value() + "}";
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.squaredDistanceTo(x, group.topY, z) <= (double) notifyRadius * notifyRadius) {
					PlayerEvents.send(player, event, json);
				}
			}
		}
		LoreDevLog.info("[harvest] coin {}{} dropped at {} {} {}", denom.value(),
				count > 1 ? " ×" + count + " (discoverer's chestplate)" : "",
				(int) x, (int) group.topY, (int) z);
	}

	private boolean pioneerBonus(ServerWorld world, Ascension group) {
		if (pioneerSlot < 0 || pioneerCoins <= 1 || group.author == null) {
			return false;
		}
		ServerPlayerEntity author = world.getServer().getPlayerManager().getPlayer(group.author);
		if (author == null) {
			return false;
		}
		ItemStack worn = author.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
		return !worn.isEmpty() && worn.getItem() == LoreItemPool.get(pioneerSlot);
	}

	private static Coins.Denom denomFor(int value) {
		for (Coins.Denom d : Coins.descending()) {
			if (d.value() == value) {
				return d;
			}
		}
		return Coins.highestNotExceeding(value);
	}

	private static int colorOf(JsonObject config, String key, int fallback) {
		if (config == null || !config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			String raw = config.get(key).getAsString().replace("#", "").trim();
			return raw.isEmpty() ? fallback : Integer.parseInt(raw, 16) & 0xFFFFFF;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int clampInt(JsonObject config, String key, int fallback, int min, int max) {
		if (config == null || !config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return Math.max(min, Math.min(max, config.get(key).getAsInt()));
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double clampDouble(JsonObject config, String key, double fallback, double min, double max) {
		if (config == null || !config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return Math.max(min, Math.min(max, config.get(key).getAsDouble()));
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}
}
