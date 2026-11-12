package com.rufana.instantlore.client.dialog;

import com.google.gson.JsonObject;
import com.rufana.instantlore.client.LoreReports;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public final class DialogScreen extends Screen {
	private final String id;
	private final Dialog dialog;
	private List<OrderedText> body = List.of();
	private boolean chosen;

	public DialogScreen(String id, Dialog dialog) {
		super(Text.literal(dialog.title()));
		this.id = id;
		this.dialog = dialog;
	}

	@Override
	protected void init() {
		int textWidth = Math.min(this.width - 40, 320);
		this.body = this.textRenderer.wrapLines(Text.literal(this.dialog.text()), textWidth);

		int buttonWidth = Math.min(this.width - 40, 240);
		int x = (this.width - buttonWidth) / 2;
		int y = this.height / 2;

		for (Dialog.Option option : this.dialog.options()) {
			this.addDrawableChild(ButtonWidget
					.builder(Text.literal(option.label()), button -> choose(option.key()))
					.dimensions(x, y, buttonWidth, 20)
					.build());
			y += 24;
		}
	}

	private void choose(String key) {
		this.chosen = true;

		JsonObject data = new JsonObject();
		data.addProperty("id", this.id);
		data.addProperty("key", key);
		LoreReports.sendAndPublish("dialog.choice", data);

		this.client.setScreen(null);
	}

	@Override
	public void close() {
		if (!this.chosen) {
			JsonObject data = new JsonObject();
			data.addProperty("id", this.id);
			LoreReports.sendAndPublish("dialog.cancelled", data);
		}

		this.client.setScreen(null);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(this.textRenderer, getTitle(),
				this.width / 2, this.height / 4 - 24, 0xFFFFFF);

		int y = this.height / 4;
		for (OrderedText line : this.body) {
			context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, 0xFFFFFF);
			y += this.textRenderer.fontHeight + 2;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
