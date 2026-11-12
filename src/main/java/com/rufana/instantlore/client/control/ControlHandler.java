package com.rufana.instantlore.client.control;

import com.google.gson.JsonArray;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.camera.CameraEffects;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.control.KeyGate;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class ControlHandler {
	private ControlHandler() {
	}

	public static void register() {
		LoreEventBus.subscribe("player.control", event -> PlayerControl.begin(
				event.bool("lock_look", true),
				event.bool("lock_move", true),
				event.bool("ignore_external_velocity", true),
				event.bool("no_entity_push", true),
				event.number("timeout", 30)));

		LoreEventBus.subscribe("player.release", event -> PlayerControl.release("команда сервера"));

		LoreEventBus.subscribe("player.goto", event -> {
			Vec3d point = new Vec3d(
					event.number("x", 0), event.number("y", 0), event.number("z", 0));

			PlayerControl.setTarget(point,
					event.string("speed", "walk"),
					event.number("arrive", 0),
					event.bool("face", true));
		});

		LoreEventBus.subscribe("player.path", event -> {
			List<PlayerControl.Waypoint> points = parsePoints(event);

			if (points.isEmpty()) {
				InstantLore.LOGGER.warn("player.path without points");
				return;
			}

			PlayerControl.setPath(points,
					event.string("speed", "walk"),
					event.number("arrive", 0),
					event.bool("face", true));

			InstantLore.LOGGER.info("Route accepted: points {}", points.size());
		});

		LoreEventBus.subscribe("player.look", event -> {
			double turn = event.number("turn", 0);

			if (event.data().has("x") && event.data().has("z")) {
				PlayerControl.lookAt(new Vec3d(
						event.number("x", 0), event.number("y", 0), event.number("z", 0)), turn);
				return;
			}

			if (event.data().has("yaw_delta") || event.data().has("pitch_delta")) {
				var player = net.minecraft.client.MinecraftClient.getInstance().player;

				if (player == null) {
					return;
				}

				Float yaw = event.data().has("yaw_delta")
						? player.getYaw() + (float) event.number("yaw_delta", 0)
						: null;
				Float pitch = event.data().has("pitch_delta")
						? player.getPitch() + (float) event.number("pitch_delta", 0)
						: null;

				PlayerControl.lookAngles(yaw, pitch, turn);
				return;
			}

			if (event.data().has("yaw") || event.data().has("pitch")) {
				PlayerControl.lookAngles(
						event.data().has("yaw") ? (float) event.number("yaw", 0) : null,
						event.data().has("pitch") ? (float) event.number("pitch", 0) : null,
						turn);
				return;
			}

			PlayerControl.freeLook();
		});

		LoreEventBus.subscribe("player.input", event -> PlayerControl.input(
				event.number("forward", 0),
				event.number("strafe", 0),
				event.bool("jump", false),
				event.bool("sneak", false),
				event.bool("sprint", false),
				event.number("seconds", 0)));

		LoreEventBus.subscribe("player.uninput", event -> PlayerControl.releaseInput());

		LoreEventBus.subscribe("player.keys", event -> {
			if (event.bool("block_all", false)) {
				KeyGate.blockAllExcept(strings(event, "except"), event.number("seconds", 0));
				return;
			}

			KeyGate.block(strings(event, "block"), event.number("seconds", 0));
		});

		//
		//
		LoreEventBus.subscribe("player.keys_ask", event -> {
			var options = net.minecraft.client.MinecraftClient.getInstance().options;
			var keys = new com.google.gson.JsonArray();

			for (var binding : options.allKeys) {
				keys.add(binding.getId());
			}

			var data = new com.google.gson.JsonObject();
			data.addProperty("count", keys.size());
			data.add("keys", keys);
			com.rufana.instantlore.client.LoreReports.send("player.keys_list", data);
		});

		LoreEventBus.subscribe("player.hotbar", event -> {
			int slot = (int) event.number("slot", -1);

			if (slot < 0 || slot > 8) {
				InstantLore.LOGGER.warn("player.hotbar: slot {} outside 0..8", slot);
				return;
			}

			var player = net.minecraft.client.MinecraftClient.getInstance().player;

			if (player != null) {
				player.getInventory().setSelectedSlot(slot);
			}
		});

		LoreEventBus.subscribe("camera.shake", event -> CameraEffects.shake(
				event.number("amplitude", 0),
				event.number("frequency", 0),
				event.number("seconds", 0)));

		LoreEventBus.subscribe("camera.fov", event -> CameraEffects.fov(
				event.number("multiplier", 0), event.number("seconds", 0)));

		LoreEventBus.subscribe("camera.perspective", event ->
				CameraEffects.perspective(event.string("mode", "first")));

		LoreEventBus.subscribe("screen.fade", event -> {
			if (event.bool("clear", false)) {
				com.rufana.instantlore.client.hud.ScreenFade.clear();
				return;
			}

			com.rufana.instantlore.client.hud.ScreenFade.fade(
					event.string("color", "#FF000000"),
					event.number("in", 0.5),
					event.number("hold", 0),
					event.number("out", 0.5));
		});

		LoreEventBus.subscribe("glow.direction", event -> {
			if (event.bool("clear", false)) {
				com.rufana.instantlore.client.hud.DirectionGlow.clear();
				return;
			}

			com.rufana.instantlore.client.hud.DirectionGlow.glow(
					event.number("yaw", 0),
					event.string("color", "#A8F5C77A"),
					event.number("in", 0.35),
					event.number("hold", 1.4),
					event.number("out", 2.25));
		});

		LoreEventBus.subscribe("player.click", event -> ClickControl.click(
				event.string("button", "left"), (int) event.number("count", 1)));

		LoreEventBus.subscribe("player.hold", event -> ClickControl.hold(
				event.string("button", "left"), event.number("seconds", 0)));

		LoreEventBus.subscribe("player.unhold", event ->
				ClickControl.releaseButton(event.string("button", "left")));
	}

	private static List<PlayerControl.Waypoint> parsePoints(
			com.rufana.instantlore.event.LoreEvent event) {
		List<PlayerControl.Waypoint> points = new ArrayList<>();

		if (!event.data().has("points")) {
			return points;
		}

		JsonArray array = event.data().getAsJsonArray("points");

		for (var element : array) {
			try {
				JsonArray triple = element.getAsJsonArray();

				Vec3d at = new Vec3d(
						triple.get(0).getAsDouble(),
						triple.get(1).getAsDouble(),
						triple.get(2).getAsDouble());

				String speed = triple.size() > 3 && triple.get(3).isJsonPrimitive()
						? triple.get(3).getAsString()
						: null;

				points.add(new PlayerControl.Waypoint(at, speed));
			} catch (Exception e) {
				InstantLore.LOGGER.warn("Route point failed to parse: {}", element);
			}
		}

		return points;
	}

	private static List<String> strings(com.rufana.instantlore.event.LoreEvent event, String key) {
		List<String> out = new ArrayList<>();

		if (!event.data().has(key) || !event.data().get(key).isJsonArray()) {
			return out;
		}

		for (var element : event.data().getAsJsonArray(key)) {
			if (element.isJsonPrimitive()) {
				out.add(element.getAsString());
			}
		}

		return out;
	}
}
