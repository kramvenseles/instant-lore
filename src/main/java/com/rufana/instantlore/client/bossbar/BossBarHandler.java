package com.rufana.instantlore.client.bossbar;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.text.Placeholders;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class BossBarHandler {
	private BossBarHandler() {
	}

	private static UUID uuidFor(String id) {
		return UUID.nameUUIDFromBytes(("instantlore:bossbar:" + id).getBytes(StandardCharsets.UTF_8));
	}

	private static BossBarHud hud() {
		return MinecraftClient.getInstance().inGameHud.getBossBarHud();
	}

	public static void clearAll() {
		hud().clear();
	}

	public static void register() {
		LoreEventBus.subscribe("bossbar.show", event -> {
			String id = event.string("id", null);

			if (id == null) {
				InstantLore.LOGGER.warn("bossbar.show without id field");
				return;
			}

			Map<String, String> values = event.data().has("values")
					? Placeholders.flatten(event.data().getAsJsonObject("values"))
					: Map.of();

			ClientBossBar bar = new ClientBossBar(
					uuidFor(id),
					Text.literal(Placeholders.substitute(event.string("title", ""), values)),
					clamp01((float) event.number("percent", 1.0)),
					color(event.string("color", "purple")),
					style(event.string("style", "progress")),
					false, false, false);

			hud().handlePacket(BossBarS2CPacket.add(bar));
		});

		LoreEventBus.subscribe("bossbar.set", event -> {
			String id = event.string("id", null);

			if (id == null) {
				return;
			}

			UUID uuid = uuidFor(id);

			if (event.data().has("percent")) {
				ClientBossBar bar = new ClientBossBar(uuid, Text.empty(),
						clamp01((float) event.number("percent", 1.0)),
						BossBar.Color.PURPLE, BossBar.Style.PROGRESS, false, false, false);
				hud().handlePacket(BossBarS2CPacket.updateProgress(bar));
			}

			if (event.data().has("title")) {
				Map<String, String> values = event.data().has("values")
						? Placeholders.flatten(event.data().getAsJsonObject("values"))
						: Map.of();

				ClientBossBar bar = new ClientBossBar(uuid,
						Text.literal(Placeholders.substitute(event.string("title", ""), values)),
						1.0F, BossBar.Color.PURPLE, BossBar.Style.PROGRESS, false, false, false);
				hud().handlePacket(BossBarS2CPacket.updateName(bar));
			}
		});

		LoreEventBus.subscribe("bossbar.hide", event -> {
			String id = event.string("id", null);

			if (id == null) {
				hud().clear();
				return;
			}

			hud().handlePacket(BossBarS2CPacket.remove(uuidFor(id)));
		});
	}

	private static float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private static BossBar.Color color(String name) {
		try {
			return BossBar.Color.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			return BossBar.Color.PURPLE;
		}
	}

	private static BossBar.Style style(String name) {
		try {
			return BossBar.Style.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			return BossBar.Style.PROGRESS;
		}
	}
}
