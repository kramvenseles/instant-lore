package com.rufana.instantlore.server.integration.voicechat;

import com.rufana.instantlore.server.LoreDevLog;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VcReconnect {

	private static final int GRACE_TICKS = 300;

	private static final int RETRY_TICKS = 100;

	private static final int MAX_RESENDS = 2;

	private static final Map<UUID, Watch> WAITING = new ConcurrentHashMap<>();

	private record Watch(int ticksLeft, int resendsLeft, long loginMillis, String name) {
	}

	private VcReconnect() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VcReconnect::onTick);
	}

	public static void onAuthenticated(ServerPlayerEntity player) {
		if (player == null || !FabricLoader.getInstance().isModLoaded("voicechat")) {
			return;
		}
		String name = player.getName().getString();
		WAITING.put(player.getUuid(), new Watch(GRACE_TICKS, MAX_RESENDS, System.currentTimeMillis(), name));
		LoreDevLog.info("[vc] {} passed /login - watching voice, grace {}", name, sec(GRACE_TICKS));
	}

	public static void onVoiceConnected(UUID player) {
		if (player == null) {
			return;
		}
		Watch w = WAITING.remove(player);
		if (w != null) {
			LoreDevLog.info("[vc] {} voice CAME UP after {} (voicechat event)",
					w.name(), sinceLogin(w));
		} else {
			LoreDevLog.info("[vc] {} voice connected (outside tracking window)", player);
		}
	}

	public static void onVoiceDisconnected(UUID player) {
		if (player != null) {
			LoreDevLog.info("[vc] {} voice DROPPED", player);
		}
	}

	public static void forget(UUID player) {
		if (player != null) {
			WAITING.remove(player);
		}
	}

	private static void onTick(net.minecraft.server.MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}

		VcState.SpyBridge bridge = VcState.spyBridge();
		if (bridge == null) {
			WAITING.clear();
			return;
		}

		for (Map.Entry<UUID, Watch> entry : WAITING.entrySet()) {
			UUID uuid = entry.getKey();
			Watch w = entry.getValue();

			if (w.ticksLeft() > 1) {
				WAITING.put(uuid, new Watch(w.ticksLeft() - 1, w.resendsLeft(), w.loginMillis(), w.name()));
				continue;
			}

			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null) {
				WAITING.remove(uuid);
				LoreDevLog.info("[vc] {} left after {} - stopping tracking", w.name(), sinceLogin(w));
				continue;
			}

			String dbg = bridge.voiceDebug(uuid);
			int state = bridge.voiceState(uuid);
			String elapsed = sinceLogin(w);

			if (state == VcState.SpyBridge.VOICE_OK) {
				WAITING.remove(uuid);
				LoreDevLog.info("[vc] {} voice already up after {} ({})", w.name(), elapsed, dbg);
				continue;
			}
			if (state == VcState.SpyBridge.VOICE_NOT_INSTALLED) {
				WAITING.remove(uuid);
				LoreDevLog.info("[vc] {} without voicechat after {} ({}) - giving up", w.name(), elapsed, dbg);
				continue;
			}

			if (w.resendsLeft() <= 0) {
				WAITING.remove(uuid);
				LoreDevLog.info("[vc] {} voice did NOT come up after {} ({}) - giving up (re-auth did not help; "
						+ "looks like the client's UDP does not get through)", w.name(), elapsed, dbg);
				continue;
			}

			boolean sent;
			try {
				sent = resendSecret(player);
			} catch (Throwable t) {
				LoreDevLog.info("[vc] re-auth {}: {}", w.name(), t.toString());
				sent = false;
			}

			if (!sent) {
				WAITING.remove(uuid);
				LoreDevLog.info("[vc] {} has no active secret after {} ({}) - giving up", w.name(), elapsed, dbg);
				continue;
			}

			int resendNo = MAX_RESENDS - w.resendsLeft() + 1;
			LoreDevLog.info("[vc] {} re-auth {}/{} after {} ({}) - resent secret, waiting {}",
					w.name(), resendNo, MAX_RESENDS, elapsed, dbg, sec(RETRY_TICKS));
			WAITING.put(uuid, new Watch(RETRY_TICKS, w.resendsLeft() - 1, w.loginMillis(), w.name()));
		}
	}

	private static boolean resendSecret(ServerPlayerEntity player) throws Exception {
		Class<?> vc = Class.forName("de.maxhenkel.voicechat.Voicechat");
		Object serverEvents = vc.getField("SERVER").get(null);          // ServerVoiceEvents
		if (serverEvents == null) {
			return false;
		}
		Object serverConfig = vc.getField("SERVER_CONFIG").get(null);   // ServerConfig
		Object voiceServer = serverEvents.getClass().getMethod("getServer").invoke(serverEvents); // Server
		if (voiceServer == null) {
			return false;
		}

		UUID uuid = player.getUuid();
		boolean hasSecret = (boolean) voiceServer.getClass()
				.getMethod("hasSecret", UUID.class).invoke(voiceServer, uuid);
		if (!hasSecret) {
			return false;
		}

		Object secret = voiceServer.getClass().getMethod("getSecret", UUID.class)
				.invoke(voiceServer, uuid);
		int port = (int) voiceServer.getClass().getMethod("getPort").invoke(voiceServer);

		Class<?> secretClass = Class.forName("de.maxhenkel.voicechat.voice.common.Secret");
		Class<?> configClass = Class.forName("de.maxhenkel.voicechat.config.ServerConfig");
		Class<?> secretPacketClass = Class.forName("de.maxhenkel.voicechat.net.SecretPacket");
		Constructor<?> ctor = secretPacketClass.getConstructor(
				ServerPlayerEntity.class, secretClass, int.class, configClass);
		Object packet = ctor.newInstance(player, secret, port, serverConfig);

		Class<?> netManagerClass = Class.forName("de.maxhenkel.voicechat.net.NetManager");
		Class<?> packetBase = Class.forName("de.maxhenkel.voicechat.net.Packet");
		netManagerClass.getMethod("sendToClient", ServerPlayerEntity.class, packetBase)
				.invoke(null, player, packet);
		return true;
	}

	private static String sec(int ticks) {
		return String.format(java.util.Locale.ROOT, "%.1fс", ticks / 20.0);
	}

	private static String sinceLogin(Watch w) {
		return String.format(java.util.Locale.ROOT, "%.1fс", (System.currentTimeMillis() - w.loginMillis()) / 1000.0);
	}
}
