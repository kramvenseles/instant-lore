package com.rufana.instantlore.client.event;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.event.LoreEvents;
import com.rufana.instantlore.net.ContentSources;

import java.util.Base64;

public final class BuiltinHandlers {
	private BuiltinHandlers() {
	}

	public static void register() {
		LoreEventBus.subscribe("lore.log", event ->
				InstantLore.LOGGER.info("[content] {}", event.string("message", "(empty)")));

		LoreEventBus.subscribe(LoreEvents.CONTENT_UPDATED, event ->
				InstantLore.LOGGER.info("Bus: content updated to '{}'", event.string("version", "?")));

		//
		LoreEventBus.subscribe("lore.sources", event -> {
			byte[] doc = decode(event.string("doc", ""));
			byte[] sig = decode(event.string("sig", ""));

			if (doc == null || sig == null) {
				InstantLore.LOGGER.warn("Server sent the source list as unreadable base64");
				return;
			}

			ContentSources.accept(doc, sig, "от сервера", true);
		});

		LoreEventBus.subscribe("lore.ghost", event -> {
			java.util.Set<java.util.UUID> uuids = new java.util.HashSet<>();
			com.google.gson.JsonElement arr = event.data().get("uuids");
			if (arr != null && arr.isJsonArray()) {
				for (com.google.gson.JsonElement e : arr.getAsJsonArray()) {
					try {
						uuids.add(java.util.UUID.fromString(e.getAsString()));
					} catch (RuntimeException ignored) {
					}
				}
			}
			com.rufana.instantlore.client.ClientGhosts.set(uuids);
			InstantLore.LOGGER.debug("Ghost list from server: {}", uuids.size());
		});
	}

	private static byte[] decode(String base64) {
		try {
			return base64.isEmpty() ? null : Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
