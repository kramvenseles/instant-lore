package com.rufana.instantlore.client;

import com.google.gson.JsonObject;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.event.LoreEvent;
import com.rufana.instantlore.net.LoreReportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LoreReports {
	private LoreReports() {
	}

	public static void send(String type, JsonObject data) {
		try {
			if (!ClientPlayNetworking.canSend(LoreReportPayload.ID)) {
				return;
			}

			ClientPlayNetworking.send(new LoreReportPayload(type, data.toString()));
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to send report '{}'", type, e);
		}
	}

	public static void sendAndPublish(String type, JsonObject data) {
		send(type, data);

		try {
			LoreEventBus.publish(new LoreEvent(type, data));
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Local publishing of report '{}' failed", type, e);
		}
	}

	public static void sendAt(String type, double x, double y, double z, JsonObject extra) {
		JsonObject data = extra == null ? new JsonObject() : extra;

		data.addProperty("x", round(x));
		data.addProperty("y", round(y));
		data.addProperty("z", round(z));

		send(type, data);
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
