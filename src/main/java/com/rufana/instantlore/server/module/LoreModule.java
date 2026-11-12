package com.rufana.instantlore.server.module;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

public interface LoreModule {

	String id();

	String title();

	void configure(JsonObject config);

	default void onEnable(MinecraftServer server) {
	}

	default void onDisable(MinecraftServer server) {
	}

	default void tick(MinecraftServer server) {
	}

	default String describe() {
		return "";
	}

	default boolean defaultEnabled() {
		return false;
	}
}
