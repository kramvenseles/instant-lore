package com.rufana.instantlore.server.task;

import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

public interface TaskStep {
	String describe();

	void start(ServerPlayerEntity player);

	default boolean onReport(ServerPlayerEntity player, String type, JsonObject data) {
		return false;
	}

	default boolean onTick(ServerPlayerEntity player) {
		return false;
	}

	double timeoutSeconds();

	default boolean failsOnTimeout() {
		return true;
	}
}
