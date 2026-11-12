package com.rufana.instantlore.client.hud;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.cutscene.CutscenePlayer;
import com.rufana.instantlore.client.ui.UiPaint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Map;

public final class HudRenderer {
	private static int reportedGeneration = -1;

	private HudRenderer() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(HudRenderer::render);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				ScreenEvents.afterRender(screen).register(
						(s, context, mouseX, mouseY, delta) -> ScreenFade.render(context)));
	}

	private static void render(DrawContext context, Object tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null) {
			return;
		}

		if (!CutscenePlayer.isActive() || !CutscenePlayer.hidesHud()) {
			renderHud(context, client);

			CrosshairRenderer.render(context);

			if (!CutscenePlayer.isActive()) {
				DirectionGlow.render(context);
			}
		}

		ScreenFade.render(context);
	}

	private static void renderHud(DrawContext context, MinecraftClient client) {
		boolean report = HudState.generation() != reportedGeneration;

		if (report) {
			reportedGeneration = HudState.generation();
		}

		HudState.shown().forEach((id, layout) -> {
			Map<String, String> values = HudState.values(id);

			if (report) {
				layout.elements().forEach(element ->
						InstantLore.LOGGER.info("HUD '{}' draws {}: '{}'",
								id, element.type(),
								"bar".equals(element.type())
										? substitute(element.value(), values)
										: substitute(element.text(), values)));
			}

			for (HudLayout.Element element : layout.elements()) {
				try {
					draw(context, client, element, values);
				} catch (Throwable e) {
				}
			}
		});
	}

	private static void draw(DrawContext context, MinecraftClient client,
			HudLayout.Element element, Map<String, String> values) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		int x = anchorX(element.anchor(), screenWidth) + element.x();
		int y = anchorY(element.anchor(), screenHeight) + element.y();

		switch (element.type()) {
			case "rect" -> context.fill(x, y, x + element.width(), y + element.height(),
					element.color());

			case "bar" -> {
				double fraction = Math.clamp(number(element.value(), values), 0.0, 1.0);
				int filled = (int) Math.round(element.width() * fraction);

				context.fill(x, y, x + element.width(), y + element.height(), element.background());
				context.fill(x, y, x + filled, y + element.height(), element.color());
			}

			case "image" -> UiPaint.image(context, substitute(element.texture(), values),
					x, y, element.width(), element.height());

			case "item" -> UiPaint.item(context, client.textRenderer,
					substitute(element.item(), values), element.count(), x, y);

			case "line" -> UiPaint.line(context, x, y,
					anchorX(element.anchor(), screenWidth) + element.x2(),
					anchorY(element.anchor(), screenHeight) + element.y2(),
					element.color(), element.thickness());

			default -> UiPaint.text(context, client.textRenderer,
					substitute(element.text(), values), x, y,
					element.color(), element.scale(), element.align());
		}
	}

	static String substitute(String template, Map<String, String> values) {
		return com.rufana.instantlore.client.text.Placeholders.substitute(template, values);
	}

	private static double number(String expression, Map<String, String> values) {
		try {
			return Double.parseDouble(substitute(expression, values));
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	static int anchorX(String anchor, int width) {
		if (anchor.equals("center") || anchor.endsWith("_center")) {
			return width / 2;
		}
		return anchor.endsWith("_right") ? width : 0;
	}

	static int anchorY(String anchor, int height) {
		if (anchor.startsWith("center")) {
			return height / 2;
		}
		return anchor.startsWith("bottom") ? height : 0;
	}
}
