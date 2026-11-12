package com.rufana.instantlore.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimboHail {
	private static final long COOLDOWN_MS = 10_000L;

	private static final String GLOW_TEMPLATE =
			"{\"yaw\":%.1f,\"color\":\"#A8F5C77A\",\"in\":0.35,\"hold\":1.4,\"out\":2.25}";

	private static final Map<String, Long> LAST_HAIL = new ConcurrentHashMap<>();

	private static final int PRUNE_THRESHOLD = 512;

	private LimboHail() {
	}

	public static int onChat(ServerPlayerEntity sender, String message) {
		if (sender == null || message == null || message.isBlank() || !inLimbo(sender)) {
			return 0;
		}

		if (InvisibleModule.isHidden(sender.getUuid())) {
			return 0;
		}

		MinecraftServer server = sender.getEntityWorld().getServer();

		if (server == null) {
			return 0;
		}

		int lit = 0;
		String lowered = message.toLowerCase(Locale.ROOT);

		for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
			if (target == sender || !inLimbo(target)) {
				continue;
			}

			if (!mentions(lowered, target.getName().getString())) {
				continue;
			}

			if (onCooldown(sender.getUuid(), target.getUuid())) {
				continue;
			}

			double yaw = yawTo(target, sender);

			hail(target, yaw);
			lit++;

			LoreDevLog.info("Limbo hail: {} → {} ({}°, {} bl)", sender.getName().getString(),
					target.getName().getString(), String.format(Locale.ROOT, "%.1f", yaw),
					Math.round(target.distanceTo(sender)));
		}

		return lit;
	}

	public static void hail(ServerPlayerEntity target, double yaw) {
		PlayerEvents.send(target, "glow.direction",
				String.format(Locale.ROOT, GLOW_TEMPLATE, yaw));
	}

	public static double yawTo(ServerPlayerEntity observer, ServerPlayerEntity source) {
		double dx = source.getX() - observer.getX();
		double dz = source.getZ() - observer.getZ();

		return -Math.toDegrees(Math.atan2(dx, dz));
	}

	private static boolean mentions(String loweredMessage, String name) {
		String needle = name.toLowerCase(Locale.ROOT);

		if (needle.isEmpty()) {
			return false;
		}

		int from = 0;

		while (true) {
			int at = loweredMessage.indexOf(needle, from);

			if (at < 0) {
				return false;
			}

			if (at == 0 || !Character.isLetterOrDigit(loweredMessage.charAt(at - 1))) {
				return true;
			}

			from = at + 1;
		}
	}

	private static boolean inLimbo(ServerPlayerEntity player) {
		return player.getEntityWorld().getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
	}

	private static boolean onCooldown(UUID sender, UUID target) {
		long now = System.currentTimeMillis();
		String key = sender + ">" + target;
		Long last = LAST_HAIL.get(key);

		if (last != null && now - last < COOLDOWN_MS) {
			return true;
		}

		if (LAST_HAIL.size() > PRUNE_THRESHOLD) {
			LAST_HAIL.entrySet().removeIf(entry -> now - entry.getValue() >= COOLDOWN_MS);
		}

		LAST_HAIL.put(key, now);
		return false;
	}
}
