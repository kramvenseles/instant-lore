package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.LoreModsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LoreModReport {

	private LoreModReport() {
	}

	public static void send() {
		try {
			if (!ClientPlayNetworking.canSend(LoreModsPayload.ID)) {
				return;
			}

			List<String> all = collect();
			List<String> capped = all.size() > LoreModsPayload.MAX_ENTRIES
					? all.subList(0, LoreModsPayload.MAX_ENTRIES)
					: all;

			ClientPlayNetworking.send(new LoreModsPayload(List.copyOf(capped), all.size()));
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to send mod list", e);
		}
	}

	private static List<String> collect() {
		Collection<ModContainer> containers = FabricLoader.getInstance().getAllMods();
		List<String> out = new ArrayList<>(containers.size());

		for (ModContainer container : containers) {
			if (container.getContainingMod().isPresent()) {
				continue;
			}

			ModMetadata meta = container.getMetadata();
			String line = meta.getId() + " " + meta.getVersion().getFriendlyString();

			out.add(line.length() > LoreModsPayload.MAX_ENTRY_LENGTH
					? line.substring(0, LoreModsPayload.MAX_ENTRY_LENGTH)
					: line);
		}

		out.sort(String::compareTo);
		return out;
	}
}
