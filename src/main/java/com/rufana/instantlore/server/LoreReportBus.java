package com.rufana.instantlore.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoreReportBus {
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	private LoreReportBus() {
	}

	@FunctionalInterface
	public interface Listener {
		void onReport(ServerPlayerEntity player, String type, JsonObject data);
	}

	public static void subscribe(Listener listener) {
		LISTENERS.add(listener);
	}

	public static void dispatch(ServerPlayerEntity player, String type, String json) {
		JsonObject data;

		try {
			data = JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			InstantLore.LOGGER.warn("Report '{}' from {} has malformed JSON - dropped",
					type, player.getName().getString());
			return;
		}

		LoreDevLog.info("Report from {}: {} {}", player.getName().getString(), type, data);

		for (Listener listener : LISTENERS) {
			try {
				listener.onReport(player, type, data);
			} catch (Throwable e) {
				InstantLore.LOGGER.error("Report handler '{}' failed", type, e);
			}
		}
	}
}
