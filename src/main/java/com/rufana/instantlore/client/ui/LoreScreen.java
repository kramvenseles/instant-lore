package com.rufana.instantlore.client.ui;

import com.google.gson.JsonObject;
import com.rufana.instantlore.client.LoreReports;
import com.rufana.instantlore.client.text.Placeholders;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LoreScreen extends Screen {
	private static final int SCROLL_STEP = 16;

	private final String id;
	private final UiScreen definition;
	private final Map<String, String> values;
	private final ScreenActions actions;
	private final Map<String, Integer> scrollOffsets = new HashMap<>();
	private final Map<String, TextFieldWidget> inputs = new HashMap<>();

	private final Map<UiNode, ButtonWidget> buttons = new java.util.IdentityHashMap<>();
	private boolean closeReported;

	private float frameDelta;

	public LoreScreen(String id, UiScreen definition, Map<String, String> values) {
		this(id, definition, values, null);
	}

	public LoreScreen(String id, UiScreen definition, Map<String, String> values, ScreenActions actions) {
		super(Text.literal(definition.title()));
		this.id = id;
		this.definition = definition;
		this.values = values;
		this.actions = actions;
	}

	private int anchorX(String anchor) {
		if (anchor.equals("center") || anchor.endsWith("_center")) {
			return this.width / 2;
		}
		return anchor.endsWith("_right") ? this.width : 0;
	}

	private int anchorY(String anchor) {
		if (anchor.startsWith("center")) {
			return this.height / 2;
		}
		return anchor.startsWith("bottom") ? this.height : 0;
	}


	@Override
	protected void init() {
		this.inputs.clear();
		this.buttons.clear();
		for (UiNode node : this.definition.nodes()) {
			createWidgets(node, anchorX(node.anchor()) + node.x(), anchorY(node.anchor()) + node.y());
		}
	}

	private void createWidgets(UiNode node, int x, int y) {
		if (node.isButton()) {
			createButton(node, x, y);
		}

		if ("input".equals(node.type()) && node.id() != null) {
			TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y,
					Math.max(node.width(), 20), Math.max(node.height(), 12), Text.empty());
			field.setMaxLength(node.maxLength());
			if (!node.placeholder().isEmpty()) {
				field.setPlaceholder(Text.literal(Placeholders.substitute(node.placeholder(), this.values)));
			}
			if (!node.text().isEmpty()) {
				field.setText(Placeholders.substitute(node.text(), this.values));
			}
			this.inputs.put(node.id(), field);
			addSelectableChild(field);
		}

		for (UiNode child : node.children()) {
			createWidgets(child, x + child.x(), y + child.y());
		}
	}

	private void dispatch(String action) {
		dispatch(action, -1, -1);
	}

	private void createButton(UiNode node, int x, int y) {
		String label = Placeholders.substitute(node.text(), this.values);

		ButtonWidget button = ButtonWidget
				.builder(Text.literal(label), widget -> dispatch(node.action()))
				.dimensions(x, y,
						node.width() > 0 ? node.width() : 200,
						node.height() > 0 ? node.height() : 20)
				.build();

		button.active = node.enabled() && node.clickable();

		this.buttons.put(node, button);
		addSelectableChild(button);
	}


	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		this.frameDelta = delta;

		for (UiNode node : this.definition.nodes()) {
			try {
				drawAt(context, node, anchorX(node.anchor()) + node.x(), anchorY(node.anchor()) + node.y(),
						mouseX, mouseY);
			} catch (Throwable e) {
			}
		}

		for (TextFieldWidget field : this.inputs.values()) {
			field.render(context, mouseX, mouseY, delta);
		}
	}

	private void drawAt(DrawContext context, UiNode node, int x, int y, int mouseX, int mouseY) {
		switch (node.type()) {
			case "rect" -> {
				int fill = node.clickable() && inRect(x, y, node.width(), node.height(), mouseX, mouseY)
						? node.hover() : node.background();
				context.fill(x, y, x + node.width(), y + node.height(), fill);
			}
			case "group", "scroll" -> {
				if (node.background() != 0) {
					context.fill(x, y, x + node.width(), y + node.height(), node.background());
				}
			}
			case "image" -> drawImage(context, node, x, y);
			case "item" -> drawItem(context, node, x, y);
			case "line" -> drawLine(context, node, x, y);
			case "input" -> {
			}
			case "button" -> drawButton(context, node, x, y, mouseX, mouseY);
			default -> drawText(context, node, x, y);
		}

		int childY = y;
		boolean clip = node.scrollable() || node.clips();
		if (clip) {
			if (node.scrollable()) {
				childY -= scrollOffset(node);
			}
			context.enableScissor(x, y, x + node.width(), y + node.height());
		}

		for (UiNode child : node.children()) {
			drawAt(context, child, x + child.x(), childY + child.y(), mouseX, mouseY);
		}

		if (clip) {
			context.disableScissor();
		}
	}

	private void drawText(DrawContext context, UiNode node, int x, int y) {
		UiPaint.text(context, this.textRenderer, Placeholders.substitute(node.text(), this.values),
				x, y, node.color(), node.scale(), node.align());
	}

	private void drawButton(DrawContext context, UiNode node, int x, int y, int mouseX, int mouseY) {
		ButtonWidget button = this.buttons.get(node);

		if (button == null) {
			return;
		}

		button.setPosition(x, y);
		button.render(context, mouseX, mouseY, this.frameDelta);
	}

	private void drawImage(DrawContext context, UiNode node, int x, int y) {
		if (node.cropped()) {
			int sw = node.srcW() > 0 ? node.srcW() : node.texW();
			int sh = node.srcH() > 0 ? node.srcH() : node.texH();
			UiPaint.imageRegion(context, node.texture(), x, y, node.width(), node.height(),
					(float) node.srcX(), (float) node.srcY(), sw, sh, node.texW(), node.texH());
			return;
		}
		UiPaint.image(context, node.texture(), x, y, node.width(), node.height());
	}

	private void drawItem(DrawContext context, UiNode node, int x, int y) {
		UiPaint.item(context, this.textRenderer, node.item(), node.count(), x, y);
	}

	private void drawLine(DrawContext context, UiNode node, int x, int y) {
		int originX = x - node.x();
		int originY = y - node.y();
		UiPaint.line(context, x, y, originX + node.x2(), originY + node.y2(),
				node.color(), node.thickness());
	}

	private int scrollOffset(UiNode node) {
		return this.scrollOffsets.getOrDefault(scrollKey(node), 0);
	}

	private String scrollKey(UiNode node) {
		return node.id() != null ? node.id() : "";
	}


	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			//
			//
			if (getFocused() instanceof ButtonWidget) {
				setFocused(null);
			}

			return true;
		}

		List<Hit> hits = new ArrayList<>();
		for (UiNode node : this.definition.nodes()) {
			collect(node, anchorX(node.anchor()) + node.x(), anchorY(node.anchor()) + node.y(), null, hits);
		}

		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(click.x(), click.y())) {
				dispatch(hit.node.action(), (int) (click.x() - hit.x()), (int) (click.y() - hit.y()));
				return true;
			}
		}

		return false;
	}

	private void dispatch(String action, int cx, int cy) {
		Map<String, String> fields = new HashMap<>();
		this.inputs.forEach((key, field) -> fields.put(key, field.getText()));

		if (this.actions != null) {
			this.actions.onAction(action, fields, cx, cy);
			return;
		}

		JsonObject data = new JsonObject();
		data.addProperty("screen", this.id);
		data.addProperty("action", action);

		if (cx >= 0 && cy >= 0) {
			data.addProperty("cx", cx);
			data.addProperty("cy", cy);
		}

		if (!fields.isEmpty()) {
			JsonObject json = new JsonObject();
			fields.forEach(json::addProperty);
			data.add("inputs", json);
		}

		LoreReports.sendAndPublish("screen.action", data);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (super.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
			return true;
		}

		for (UiNode node : this.definition.nodes()) {
			if (scrollUnder(node, anchorX(node.anchor()) + node.x(), anchorY(node.anchor()) + node.y(),
					mouseX, mouseY, vertical)) {
				return true;
			}
		}

		return dispatchScroll(mouseX, mouseY, vertical);
	}

	private boolean dispatchScroll(double mouseX, double mouseY, double vertical) {
		if (vertical == 0) {
			return false;
		}

		int mx = (int) (mouseX - this.width / 2.0);
		int my = (int) (mouseY - this.height / 2.0);
		int dir = vertical > 0 ? 1 : -1;

		if (this.actions != null) {
			this.actions.onScroll(mx, my, dir);
			return true;
		}

		JsonObject data = new JsonObject();
		data.addProperty("screen", this.id);
		data.addProperty("mx", mx);
		data.addProperty("my", my);
		data.addProperty("dir", dir);
		LoreReports.sendAndPublish("screen.scroll", data);
		return true;
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
		if (super.mouseDragged(click, offsetX, offsetY)) {
			return true;
		}

		int mx = (int) (click.x() - this.width / 2.0);
		int my = (int) (click.y() - this.height / 2.0);

		if (this.actions != null) {
			this.actions.onDrag(mx, my, (int) offsetX, (int) offsetY);
			return true;
		}

		JsonObject data = new JsonObject();
		data.addProperty("screen", this.id);
		data.addProperty("mx", mx);
		data.addProperty("my", my);
		data.addProperty("dx", (int) offsetX);
		data.addProperty("dy", (int) offsetY);
		LoreReports.sendAndPublish("screen.drag", data);
		return true;
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.gui.Click click) {
		boolean handled = super.mouseReleased(click);

		int mx = (int) (click.x() - this.width / 2.0);
		int my = (int) (click.y() - this.height / 2.0);

		if (this.actions != null) {
			this.actions.onRelease(mx, my);
		} else {
			JsonObject data = new JsonObject();
			data.addProperty("screen", this.id);
			data.addProperty("mx", mx);
			data.addProperty("my", my);
			LoreReports.sendAndPublish("screen.release", data);
		}

		return handled;
	}

	private boolean scrollUnder(UiNode node, int x, int y, double mouseX, double mouseY, double vertical) {
		if (node.scrollable() && inRect(x, y, node.width(), node.height(), (int) mouseX, (int) mouseY)) {
			int content = 0;
			for (UiNode child : node.children()) {
				content = Math.max(content, child.y() + child.height());
			}
			int max = Math.max(0, content - node.height());
			int next = scrollOffset(node) - (int) (vertical * SCROLL_STEP);
			this.scrollOffsets.put(scrollKey(node), Math.max(0, Math.min(max, next)));
			return true;
		}

		int childY = y - (node.scrollable() ? scrollOffset(node) : 0);
		for (UiNode child : node.children()) {
			if (scrollUnder(child, x + child.x(), childY + child.y(), mouseX, mouseY, vertical)) {
				return true;
			}
		}
		return false;
	}

	private void collect(UiNode node, int x, int y, int[] clip, List<Hit> out) {
		if (node.clickable() && !node.isButton()) {
			out.add(new Hit(node, x, y, clip));
		}

		int[] childClip = clip;
		int childY = y;
		if (node.scrollable()) {
			childClip = new int[]{x, y, x + node.width(), y + node.height()};
			childY -= scrollOffset(node);
		}

		for (UiNode child : node.children()) {
			collect(child, x + child.x(), childY + child.y(), childClip, out);
		}
	}

	private static boolean inRect(int x, int y, int w, int h, double px, double py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	private record Hit(UiNode node, int x, int y, int[] clip) {
		boolean contains(double px, double py) {
			if (!inRect(x, y, node.width(), node.height(), px, py)) {
				return false;
			}
			return clip == null || (px >= clip[0] && px < clip[2] && py >= clip[1] && py < clip[3]);
		}
	}

	@Override
	public void close() {
		if (!this.closeReported) {
			this.closeReported = true;
			if (this.actions != null) {
				this.actions.onClosed();
			} else {
				JsonObject data = new JsonObject();
				data.addProperty("screen", this.id);
				LoreReports.sendAndPublish("screen.closed", data);
			}
		}
		this.client.setScreen(null);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
