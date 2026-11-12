package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimboTransfer {
	public static final RegistryKey<World> LIMBO_KEY =
			RegistryKey.of(RegistryKeys.WORLD, Identifier.of("rufana", "limbo"));

	private static final Vec3d LIMBO_SPAWN = new Vec3d(8.5, 2.0, 8.5);

	private static final String WAKE_FADE =
			"{\"color\":\"#FF050505\",\"in\":0.1,\"hold\":3.5,\"out\":4.5}";

	private static final Map<UUID, RiftAnchor> PENDING = new ConcurrentHashMap<>();

	private record RiftAnchor(RegistryKey<World> world, BlockPos pos) {
	}

	private LimboTransfer() {
	}

	public static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			RiftAnchor anchor = PENDING.remove(oldPlayer.getUuid());
			if (anchor != null) {
				transfer(newPlayer, anchor);
			}
		});
	}

	private static final String[] DEATH_FLAVOR = {
			"%s отдал свою душу, чтобы отворить врата.",
			"%s принёс себя в жертву — и разлом раскрылся.",
			"%s шагнул за грань, и врата отворились его душой.",
			"Душа %s ушла в лимбо, оставив проход открытым.",
	};

	public static void beginRitualDeath(ServerPlayerEntity player, ServerWorld world) {
		BlockPos deathPos = player.getBlockPos();
		PENDING.put(player.getUuid(), new RiftAnchor(world.getRegistryKey(), deathPos));

		world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
				player.getX(), player.getBodyY(0.6), player.getZ(), 60, 0.4, 0.6, 0.4, 0.05);

		LoreDevLog.info("Ritual death of {}: rift @ {} in {}",
				player.getName().getString(), deathPos.toShortString(),
				world.getRegistryKey().getValue());

		GameRules rules = world.getGameRules();
		boolean wasShown = rules.getValue(GameRules.SHOW_DEATH_MESSAGES);
		rules.setValue(GameRules.SHOW_DEATH_MESSAGES, false, world.getServer());

		player.damage(world, world.getDamageSources().genericKill(), Float.MAX_VALUE);

		if (wasShown) {
			rules.setValue(GameRules.SHOW_DEATH_MESSAGES, true, world.getServer());
		}

		String flavor = DEATH_FLAVOR[world.getRandom().nextInt(DEATH_FLAVOR.length)];
		world.getServer().getPlayerManager().broadcast(
				Text.literal(String.format(flavor, player.getName().getString()))
						.formatted(Formatting.DARK_RED),
				false);
	}

	private static void transfer(ServerPlayerEntity fresh, RiftAnchor anchor) {
		ServerWorld limbo = fresh.getEntityWorld().getServer().getWorld(LIMBO_KEY);

		if (limbo == null) {
			InstantLore.LOGGER.error("Limbo dimension {} not found - transfer cancelled", LIMBO_KEY.getValue());
			return;
		}

		Vec3d arrival = arrival(anchor.pos());

		PlayerEvents.send(fresh, "screen.fade", WAKE_FADE);

		fresh.teleportTo(new TeleportTarget(limbo, arrival, Vec3d.ZERO, 0.0F, 0.0F,
				TeleportTarget.NO_OP));

		//
		Vec3d limboRift = arrival.add(2.5, -1.0, 0.0);
		Vec3d overRift = center(anchor.pos());

		Rifts.openPair(LIMBO_KEY, limboRift, anchor.world(), overRift, fresh.getUuid());

		LoreDevLog.info("{} taken to limbo @ {} (exit → {})",
				fresh.getName().getString(), arrival, anchor.pos().toShortString());
	}

	private static Vec3d center(BlockPos pos) {
		return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static Vec3d arrival(BlockPos anchor) {
		return new Vec3d(anchor.getX() + 0.5, LIMBO_SPAWN.y, anchor.getZ() + 0.5);
	}

	public static Vec3d limboMirror(BlockPos anchor) {
		return arrival(anchor);
	}
}
