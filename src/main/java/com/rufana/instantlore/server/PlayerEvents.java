package com.rufana.instantlore.server;

import com.rufana.instantlore.net.LoreEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PlayerEvents {
	private PlayerEvents() {
	}

	public static void send(ServerPlayerEntity player, String type, String json) {
		ServerPlayNetworking.send(player, new LoreEventPayload(type, json));
	}

	public static void control(ServerPlayerEntity player, double timeoutSeconds) {
		send(player, "player.control", "{\"timeout\":" + timeoutSeconds + "}");
	}

	public static void control(ServerPlayerEntity player, boolean lockLook, boolean lockMove,
			double timeoutSeconds) {
		send(player, "player.control",
				"{\"lock_look\":" + lockLook + ",\"lock_move\":" + lockMove
						+ ",\"ignore_external_velocity\":false,\"no_entity_push\":false"
						+ ",\"timeout\":" + timeoutSeconds + "}");
	}

	public static void blockAllKeysExcept(ServerPlayerEntity player,
			java.util.List<String> allowed, double seconds) {
		StringBuilder json = new StringBuilder("{\"block_all\":true,\"seconds\":")
				.append(seconds).append(",\"except\":[");

		for (int i = 0; i < allowed.size(); i++) {
			json.append(i == 0 ? "" : ",").append('"').append(allowed.get(i)).append('"');
		}

		send(player, "player.keys", json.append("]}").toString());
	}

	public static void clearKeys(ServerPlayerEntity player) {
		send(player, "player.keys", "{}");
	}

	public static void release(ServerPlayerEntity player) {
		send(player, "player.release", "{}");
	}

	public static void lookAt(ServerPlayerEntity player, double x, double y, double z) {
		send(player, "player.look", "{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}");
	}

	public static void hold(ServerPlayerEntity player, String button, double seconds) {
		send(player, "player.hold",
				"{\"button\":\"" + button + "\",\"seconds\":" + seconds + "}");
	}

	public static void click(ServerPlayerEntity player, String button, int count) {
		send(player, "player.click",
				"{\"button\":\"" + button + "\",\"count\":" + count + "}");
	}
}
