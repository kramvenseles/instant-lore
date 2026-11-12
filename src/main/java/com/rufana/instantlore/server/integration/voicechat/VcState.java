package com.rufana.instantlore.server.integration.voicechat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VcState {

	private VcState() {
	}

	private static final Set<UUID> BROADCAST = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> WHISPER = ConcurrentHashMap.newKeySet();
	private static final java.util.Map<UUID, UUID> SPY_GROUP = new ConcurrentHashMap<>();
	private static final Set<UUID> SPY_TALK = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();

	// ---- broadcast ----
	public static boolean broadcast(UUID u) {
		return BROADCAST.contains(u);
	}

	public static boolean setBroadcast(UUID u, boolean on) {
		return on ? BROADCAST.add(u) : BROADCAST.remove(u);
	}

	// ---- whisper ----
	public static boolean whisper(UUID u) {
		return WHISPER.contains(u);
	}

	public static boolean setWhisper(UUID u, boolean on) {
		return on ? WHISPER.add(u) : WHISPER.remove(u);
	}

	// ---- spyjoin ----
	public static UUID spyGroup(UUID u) {
		return SPY_GROUP.get(u);
	}

	public static void setSpyGroup(UUID u, UUID group) {
		if (group == null) {
			SPY_GROUP.remove(u);
		} else {
			SPY_GROUP.put(u, group);
		}
	}

	public static boolean spyTalk(UUID u) {
		return SPY_TALK.contains(u);
	}

	public static void setSpyTalk(UUID u, boolean on) {
		if (on) {
			SPY_TALK.add(u);
		} else {
			SPY_TALK.remove(u);
		}
	}

	public static Set<UUID> online() {
		return ONLINE;
	}

	public static void addOnline(UUID u) {
		ONLINE.add(u);
	}

	public static void removeOnline(UUID u) {
		ONLINE.remove(u);
	}

	public static void clearFor(UUID u) {
		BROADCAST.remove(u);
		WHISPER.remove(u);
		SPY_GROUP.remove(u);
		SPY_TALK.remove(u);
	}


	public interface SpyBridge {
		int JOIN_OK = 0;
		int JOIN_TARGET_OFFLINE = 1;
		int JOIN_TARGET_NO_GROUP = 2;
		int JOIN_SELF_NO_CONNECTION = 3;

		int VOICE_OK = 10;
		int VOICE_NEEDS_RESEND = 11;
		int VOICE_NOT_INSTALLED = 12;

		int spyjoin(UUID admin, UUID target);

		void leave(UUID admin);

		int voiceState(UUID player);

		String voiceDebug(UUID player);
	}

	private static volatile SpyBridge spyBridge;

	public static void setSpyBridge(SpyBridge bridge) {
		spyBridge = bridge;
	}

	public static SpyBridge spyBridge() {
		return spyBridge;
	}
}
