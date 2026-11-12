package com.rufana.instantlore.client.hud;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.ui.UiPaint;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class CrosshairRenderer {
	private static final Identifier VANILLA_SPRITE = Identifier.of("minecraft", "hud/crosshair");

	private static int reportedGeneration = -1;

	private CrosshairRenderer() {
	}

	public static void render(DrawContext context) {
		CrosshairState.Crosshair crosshair = CrosshairState.current();

		if (crosshair == null) {
			return;
		}

		try {
			long now = System.currentTimeMillis();

			if (crosshair.exiting() && crosshair.finished(now)) {
				CrosshairState.clear();
				return;
			}

			int screenWidth = context.getScaledWindowWidth();
			int screenHeight = context.getScaledWindowHeight();
			int size = crosshair.size();

			int offsetX = (int) Math.round(crosshair.curX(now));
			int offsetY = (int) Math.round(crosshair.curY(now));

			if (crosshair.texture() == null) {
				int left = (screenWidth - size) / 2 + offsetX;
				int top = (screenHeight - size) / 2 + offsetY;
				context.drawGuiTexture(RenderPipelines.CROSSHAIR, VANILLA_SPRITE,
						left, top, size, size);
			} else {
				int centerX = HudRenderer.anchorX(crosshair.anchor(), screenWidth) + offsetX;
				int centerY = HudRenderer.anchorY(crosshair.anchor(), screenHeight) + offsetY;
				UiPaint.image(context, crosshair.texture(),
						centerX - size / 2, centerY - size / 2, size, size);
			}

			if (CrosshairState.generation() != reportedGeneration) {
				reportedGeneration = CrosshairState.generation();
				InstantLore.LOGGER.info("Crosshair drawn: target ({}, {}), {}, {}x{}",
						(int) Math.round(crosshair.toX()), (int) Math.round(crosshair.toY()),
						crosshair.texture() == null ? "vanilla" : crosshair.texture(), size, size);
			}
		} catch (Throwable e) {
		}
	}
}
