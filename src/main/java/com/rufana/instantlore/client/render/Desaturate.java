package com.rufana.instantlore.client.render;

import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.mixin.client.GameRendererPostInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class Desaturate {
	private static final Identifier EFFECT = Identifier.of("instantlore", "desaturate");

	private static volatile boolean wanted;

	private Desaturate() {
	}

	public static void register() {
		LoreEventBus.subscribe("desaturate.on", event -> apply(true));
		LoreEventBus.subscribe("desaturate.off", event -> apply(false));
	}

	private static void apply(boolean on) {
		wanted = on;
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.gameRenderer == null) {
			return;
		}

		if (on) {
			((GameRendererPostInvoker) client.gameRenderer).instantlore$setPostProcessor(EFFECT);
		} else {
			client.gameRenderer.clearPostProcessor();
		}
	}

	public static boolean isWanted() {
		return wanted;
	}

	public static void restoreIfLost() {
		if (!wanted) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.gameRenderer == null || EFFECT.equals(client.gameRenderer.getPostProcessorId())) {
			return;
		}
		((GameRendererPostInvoker) client.gameRenderer).instantlore$setPostProcessor(EFFECT);
	}

	public static void reset() {
		apply(false);
	}
}
