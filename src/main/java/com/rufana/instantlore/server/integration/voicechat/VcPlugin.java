package com.rufana.instantlore.server.integration.voicechat;

import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.module.InvisibleModule;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class VcPlugin implements VoicechatPlugin, VcState.SpyBridge {

	private static final float WHISPER_RANGE = 48f;

	private volatile VoicechatServerApi serverApi;

	@Override
	public String getPluginId() {
		return "instantlore";
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
		registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
		registration.registerEvent(PlayerConnectedEvent.class, this::onVoiceConnected);
		registration.registerEvent(PlayerDisconnectedEvent.class, this::onVoiceDisconnected);
	}

	private void onVoiceConnected(PlayerConnectedEvent event) {
		try {
			VoicechatConnection conn = event.getConnection();
			if (conn == null) {
				return;
			}
			ServerPlayer p = conn.getPlayer();
			if (p == null) {
				return;
			}
			VcReconnect.onVoiceConnected(p.getUuid());
		} catch (Throwable t) {
			LoreDevLog.info("[vc] onVoiceConnected: {}", t.toString());
		}
	}

	private void onVoiceDisconnected(PlayerDisconnectedEvent event) {
		try {
			VcReconnect.onVoiceDisconnected(event.getPlayerUuid());
		} catch (Throwable t) {
			LoreDevLog.info("[vc] onVoiceDisconnected: {}", t.toString());
		}
	}

	private void onServerStarted(VoicechatServerStartedEvent event) {
		this.serverApi = event.getVoicechat();
		VcState.setSpyBridge(this);
		LoreDevLog.info("[vc] instantlore voicechat plugin started");
	}


	private void onMicrophone(MicrophonePacketEvent event) {
		try {
			VoicechatConnection sender = event.getSenderConnection();
			if (sender == null) {
				return;
			}
			ServerPlayer vcPlayer = sender.getPlayer();
			if (vcPlayer == null) {
				return;
			}
			UUID u = vcPlayer.getUuid();
			VoicechatServerApi api = event.getVoicechat();
			byte[] opus = event.getPacket().getOpusEncodedData();

			if (VcState.spyGroup(u) != null && !VcState.spyTalk(u)) {
				event.cancel();
				return;
			}

			if (VcState.broadcast(u)) {
				event.cancel();
				broadcastToAll(api, u, opus);
				return;
			}

			if (InvisibleModule.isHidden(u) && VcState.whisper(u)) {
				whisperToNonStaff(api, vcPlayer, u, opus);
				return;
			}
		} catch (Throwable t) {
			LoreDevLog.info("[vc] mic handler: {}", t.toString());
		}
	}

	private void broadcastToAll(VoicechatServerApi api, UUID sender, byte[] opus) {
		UUID channelId = UUID.nameUUIDFromBytes(
				("instantlore:vc-broadcast:" + sender).getBytes(StandardCharsets.UTF_8));
		StaticAudioChannel channel = api.createStaticAudioChannel(channelId);
		if (channel == null) {
			return;
		}
		channel.setBypassGroupIsolation(true);
		channel.clearTargets();
		for (UUID uuid : VcState.online()) {
			if (uuid.equals(sender)) {
				continue;
			}
			VoicechatConnection conn = api.getConnectionOf(uuid);
			if (conn != null) {
				channel.addTarget(conn);
			}
		}
		channel.send(opus);
	}

	private void whisperToNonStaff(VoicechatServerApi api, ServerPlayer vcPlayer, UUID sender, byte[] opus) {
		Object nativeObj = vcPlayer.getPlayer();
		if (!(nativeObj instanceof ServerPlayerEntity sp)) {
			return;
		}
		ServerLevel level = api.fromServerLevel(sp.getEntityWorld());
		Position pos = api.createPosition(sp.getX(), sp.getEyeY(), sp.getZ());
		UUID channelId = UUID.nameUUIDFromBytes(
				("instantlore:vc-whisper:" + sender).getBytes(StandardCharsets.UTF_8));
		LocationalAudioChannel channel = api.createLocationalAudioChannel(channelId, level, pos);
		if (channel == null) {
			return;
		}
		channel.setDistance(WHISPER_RANGE);
		channel.updateLocation(pos);
		channel.setFilter(target -> {
			Object o = target.getPlayer();
			if (!(o instanceof ServerPlayerEntity tp)) {
				return false;
			}
			if (tp.getUuid().equals(sender)) {
				return false;
			}
			return !InvisibleModule.seesVanishedTarget(tp, sender);
		});
		channel.send(opus);
	}


	@Override
	public int spyjoin(UUID admin, UUID target) {
		VoicechatServerApi api = serverApi;
		if (api == null) {
			return JOIN_TARGET_OFFLINE;
		}
		VoicechatConnection targetConn = api.getConnectionOf(target);
		if (targetConn == null) {
			return JOIN_TARGET_OFFLINE;
		}
		Group group = targetConn.getGroup();
		if (group == null) {
			return JOIN_TARGET_NO_GROUP;
		}
		VoicechatConnection adminConn = api.getConnectionOf(admin);
		if (adminConn == null) {
			return JOIN_SELF_NO_CONNECTION;
		}
		adminConn.setGroup(group);
		VcState.setSpyGroup(admin, group.getId());
		VcState.setSpyTalk(admin, false);
		return JOIN_OK;
	}

	@Override
	public void leave(UUID admin) {
		VoicechatServerApi api = serverApi;
		if (api != null) {
			VoicechatConnection adminConn = api.getConnectionOf(admin);
			if (adminConn != null) {
				adminConn.setGroup(null);
			}
		}
		VcState.setSpyGroup(admin, null);
		VcState.setSpyTalk(admin, false);
	}

	@Override
	public int voiceState(UUID player) {
		VoicechatServerApi api = serverApi;
		if (api == null) {
			return VcState.SpyBridge.VOICE_OK;
		}
		VoicechatConnection conn = api.getConnectionOf(player);
		if (conn == null) {
			return VcState.SpyBridge.VOICE_NEEDS_RESEND;
		}
		if (!conn.isInstalled()) {
			return VcState.SpyBridge.VOICE_NOT_INSTALLED;
		}
		return conn.isConnected() ? VcState.SpyBridge.VOICE_OK : VcState.SpyBridge.VOICE_NEEDS_RESEND;
	}

	@Override
	public String voiceDebug(UUID player) {
		VoicechatServerApi api = serverApi;
		if (api == null) {
			return "api=null";
		}
		VoicechatConnection conn = api.getConnectionOf(player);
		if (conn == null) {
			return "conn=null";
		}
		String installed;
		String connected;
		try {
			installed = String.valueOf(conn.isInstalled());
		} catch (Throwable t) {
			installed = "?";
		}
		try {
			connected = String.valueOf(conn.isConnected());
		} catch (Throwable t) {
			connected = "?";
		}
		return "conn=present installed=" + installed + " connected=" + connected;
	}
}
