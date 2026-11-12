package com.rufana.instantlore.client.narrative;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.text.Placeholders;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.util.Map;

public final class NarrativeHandler {
	private NarrativeHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("title.show", event -> {
			boolean hasTitle = event.data().has("title");
			boolean hasSubtitle = event.data().has("subtitle");

			if (!hasTitle && !hasSubtitle) {
				InstantLore.LOGGER.warn("title.show without title/subtitle fields");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? Placeholders.flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			InGameHud hud = MinecraftClient.getInstance().inGameHud;

			if (event.data().has("fade_in") || event.data().has("stay") || event.data().has("fade_out")) {
				hud.setTitleTicks(
						(int) event.number("fade_in", 10),
						(int) event.number("stay", 70),
						(int) event.number("fade_out", 20));
			} else {
				hud.setDefaultTitleFade();
			}

			if (hasSubtitle) {
				hud.setSubtitle(Text.literal(Placeholders.substitute(event.string("subtitle", ""), values)));
			}

			hud.setTitle(Text.literal(Placeholders.substitute(event.string("title", ""), values)));
		});

		LoreEventBus.subscribe("title.clear", event ->
				MinecraftClient.getInstance().inGameHud.clearTitle());

		LoreEventBus.subscribe("actionbar.set", event -> {
			String text = event.string("text", null);

			if (text == null) {
				InstantLore.LOGGER.warn("actionbar.set without text field");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? Placeholders.flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			MinecraftClient.getInstance().inGameHud.setOverlayMessage(
					Text.literal(Placeholders.substitute(text, values)), false);
		});

		LoreEventBus.subscribe("toast.show", event -> {
			String title = event.string("title", null);

			if (title == null) {
				InstantLore.LOGGER.warn("toast.show without title field");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? Placeholders.flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			String description = event.string("description", "");

			SystemToast.show(
					MinecraftClient.getInstance().getToastManager(),
					SystemToast.Type.PERIODIC_NOTIFICATION,
					Text.literal(Placeholders.substitute(title, values)),
					description.isEmpty() ? null : Text.literal(Placeholders.substitute(description, values)));
		});
	}
}
