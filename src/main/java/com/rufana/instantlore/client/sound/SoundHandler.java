package com.rufana.instantlore.client.sound;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SoundHandler {
	private SoundHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("sound.play", event -> {
			String rawId = event.string("id", null);

			if (rawId == null) {
				InstantLore.LOGGER.warn("sound.play without id field");
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			ClientWorld world = client.world;

			if (world == null) {
				return;
			}

			Identifier id = Identifier.tryParse(rawId);
			SoundEvent sound = id == null ? null : Registries.SOUND_EVENT.get(id);

			if (sound == null) {
				InstantLore.LOGGER.warn("Unknown sound '{}'", rawId);
				return;
			}

			double x, y, z;
			if (event.data().has("x")) {
				x = event.number("x", 0);
				y = event.number("y", 0);
				z = event.number("z", 0);
			} else {
				ClientPlayerEntity player = client.player;
				if (player == null) {
					return;
				}
				x = player.getX();
				y = player.getY();
				z = player.getZ();
			}

			world.playSoundClient(x, y, z, sound, category(event.string("category", "ambient")),
					(float) event.number("volume", 1.0), (float) event.number("pitch", 1.0), false);
		});

		LoreEventBus.subscribe("music.play", event -> {
			String rawId = event.string("id", null);

			if (rawId == null) {
				InstantLore.LOGGER.warn("music.play without id field");
				return;
			}

			Identifier id = Identifier.tryParse(rawId);
			RegistryEntry<SoundEvent> entry = id == null
					? null
					: Registries.SOUND_EVENT.getEntry(id).orElse(null);

			if (entry == null) {
				InstantLore.LOGGER.warn("Unknown music '{}'", rawId);
				return;
			}

			MusicSound music = new MusicSound(entry, 0, 0, event.bool("replace", true));
			MinecraftClient.getInstance().getMusicTracker().play(music);
		});

		LoreEventBus.subscribe("music.stop", event ->
				MinecraftClient.getInstance().getMusicTracker().stop());
	}

	private static SoundCategory category(String name) {
		try {
			return SoundCategory.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			return SoundCategory.AMBIENT;
		}
	}
}
