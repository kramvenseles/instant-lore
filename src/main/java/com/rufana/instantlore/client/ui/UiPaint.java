package com.rufana.instantlore.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class UiPaint {
	private UiPaint() {
	}

	public static void image(DrawContext context, String texture, int x, int y, int w, int h) {
		if (texture == null || texture.isEmpty()) {
			return;
		}

		Identifier id = Identifier.tryParse(texture);

		if (id == null) {
			return;
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, w, h, w, h, w, h);
	}

	public static void imageRegion(DrawContext context, String texture, int x, int y, int w, int h,
			float u, float v, int sw, int sh, int tw, int th) {
		if (texture == null || texture.isEmpty() || tw <= 0 || th <= 0) {
			return;
		}

		Identifier id = Identifier.tryParse(texture);

		if (id == null) {
			return;
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, sw, sh, tw, th);
	}

	public static void item(DrawContext context, TextRenderer font, String itemId, int count,
			int x, int y) {
		if (itemId == null || itemId.isEmpty()) {
			return;
		}

		Identifier id = Identifier.tryParse(itemId);
		Item item = id == null ? null : Registries.ITEM.get(id);

		if (item == null) {
			return;
		}

		ItemStack stack = new ItemStack(item, Math.max(1, count));

		if (stack.isEmpty()) {
			return;
		}

		context.drawItem(stack, x, y);

		if (count > 1) {
			context.drawStackOverlay(font, stack, x, y);
		}
	}

	public static void text(DrawContext context, TextRenderer font, String text,
			int x, int y, int color, double scale, String align) {
		if (text == null || text.isEmpty()) {
			return;
		}

		double factor = scale <= 0 ? 1.0 : scale;
		int width = font.getWidth(text);
		int shift = switch (align == null ? "left" : align) {
			case "center" -> (int) Math.round(width * factor / 2);
			case "right" -> (int) Math.round(width * factor);
			default -> 0;
		};

		if (factor == 1.0) {
			context.drawTextWithShadow(font, text, x - shift, y, color);
			return;
		}

		var matrices = context.getMatrices();
		matrices.pushMatrix();
		try {
			matrices.translate((float) (x - shift), (float) y);
			matrices.scale((float) factor, (float) factor);
			context.drawTextWithShadow(font, text, 0, 0, color);
		} finally {
			matrices.popMatrix();
		}
	}

	public static void line(DrawContext context, int x1, int y1, int x2, int y2,
			int color, int thickness) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = Math.hypot(dx, dy);

		if (length < 0.5) {
			return;
		}

		int t = Math.max(1, thickness);
		var matrices = context.getMatrices();
		matrices.pushMatrix();
		try {
			matrices.translate((float) x1, (float) y1);
			matrices.rotate((float) Math.atan2(dy, dx));
			context.fill(0, -t / 2, (int) Math.round(length), t - t / 2, color);
		} finally {
			matrices.popMatrix();
		}
	}
}
