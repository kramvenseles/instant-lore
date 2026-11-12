package com.rufana.instantlore.server.integration;

import com.rufana.instantlore.InstantLore;

import net.fabricmc.loader.api.FabricLoader;

public final class PlaceholderCountersBridge {

	private PlaceholderCountersBridge() {
	}

	private static final boolean PB4_PRESENT = FabricLoader.getInstance().isModLoaded("placeholder-api");
	private static volatile boolean registered = false;

	public static void tryRegister() {
		if (!PB4_PRESENT || registered) {
			return;
		}
		try {
			PlaceholderCounters.register();
			registered = true;
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[pb4] counters failed to start - TAB header will show empty", t);
		}
	}
}
