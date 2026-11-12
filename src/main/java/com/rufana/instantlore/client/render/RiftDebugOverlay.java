package com.rufana.instantlore.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

public final class RiftDebugOverlay {
	private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();
	private static final int COLOR = 0xFF55FFFF;   // aqua

	private RiftDebugOverlay() {
	}

	public static void register() {
		if (!DEV) {
			return;
		}
		HudRenderCallback.EVENT.register(RiftDebugOverlay::render);
	}

	private static void render(DrawContext context, Object tickCounter) {
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) {
				return;
			}
			double dist = RiftEffect.nearestDistance(client.player.getEyePos());
			if (dist < 0.0) {
				return;
			}
			String text = String.format(Locale.ROOT, "разлом: %.1f бл", dist);
			int x = context.getScaledWindowWidth() / 2 - client.textRenderer.getWidth(text) / 2;
			context.drawTextWithShadow(client.textRenderer, text, x, 6, COLOR);
		} catch (Throwable ignored) {
		}
	}
}
