package com.rufana.instantlore.client.world;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.text.Placeholders;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldMarkers {
	private static final Map<String, Marker> MARKERS = new ConcurrentHashMap<>();

	private static final int MAX_LOGGED_FAILURES = 5;
	private static final java.util.concurrent.atomic.AtomicInteger FAILURES =
			new java.util.concurrent.atomic.AtomicInteger();

	private record Marker(String type, double x, double y, double z,
			double x2, double y2, double z2, double size, int color, String text,
			double radius, List<Vec3d> points, boolean fill) {
	}

	private static final int CIRCLE_SEGMENTS = 32;

	private static final int MAX_PATH_POINTS = 256;

	private WorldMarkers() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(WorldMarkers::render);

		LoreEventBus.subscribe("marker.set", event -> {
			String id = event.string("id", null);
			if (id == null) {
				InstantLore.LOGGER.warn("marker.set without id field");
				return;
			}
			MARKERS.put(id, new Marker(
					event.string("type", "box"),
					event.number("x", 0), event.number("y", 0), event.number("z", 0),
					event.number("x2", 0), event.number("y2", 0), event.number("z2", 0),
					event.number("size", 1), color(event.string("color", "#FF00E5FF")),
					Placeholders.substitute(event.string("text", ""),
							Placeholders.flatten(event.data().has("values")
									&& event.data().get("values").isJsonObject()
											? event.data().getAsJsonObject("values")
											: new com.google.gson.JsonObject())),
					event.number("radius", 2),
					points(event.data()),
					event.data().has("fill")
							&& event.data().get("fill").isJsonPrimitive()
							&& event.data().get("fill").getAsBoolean()));
		});

		LoreEventBus.subscribe("marker.debug", event ->
				MARKERS.forEach((id, m) -> InstantLore.LOGGER.info(
						"Marker '{}': type {}, points {}, radius {}, size {}",
						id, m.type(), m.points().size(), m.radius(), m.size())));

		LoreEventBus.subscribe("marker.remove", event -> {
			String id = event.string("id", null);
			if (id != null) {
				MARKERS.remove(id);
			}
		});

		LoreEventBus.subscribe("marker.clear", event -> MARKERS.clear());
	}

	public static void clear() {
		MARKERS.clear();
	}

	private static void render(WorldRenderContext context) {
		if (MARKERS.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null || MinecraftClient.getInstance().gameRenderer == null) {
			return;
		}

		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		Vec3d cam = camera.getCameraPos();

		for (Marker marker : MARKERS.values()) {
			try {
				switch (marker.type()) {
					case "line" -> drawLine(matrices, consumers, marker, cam);
					case "text" -> drawText(matrices, context, marker, cam);
					case "circle" -> drawCircle(matrices, consumers, marker, cam);
					case "path" -> drawPath(matrices, consumers, marker, cam);
					default -> drawBox(matrices, consumers, marker, cam);
				}
			} catch (Throwable e) {
				//
				if (FAILURES.incrementAndGet() <= MAX_LOGGED_FAILURES) {
					InstantLore.LOGGER.error("Marker '{}' failed to render", marker.type(), e);
				}
			}
		}
	}

	private static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers,
			Marker m, Vec3d cam) {
		double half = m.size() / 2.0;
		Box box = new Box(m.x() - half, m.y(), m.z() - half, m.x() + half, m.y() + m.size(), m.z() + half);

		if (m.fill()) {
			fillBox(matrices, consumers, box, m.color(), cam);
			return;
		}

		VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());
		VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(box),
				-cam.x, -cam.y, -cam.z, m.color(), 2.0F);
	}

	private static void fillBox(MatrixStack matrices, VertexConsumerProvider consumers,
			Box box, int color, Vec3d cam) {
		VertexConsumer quads = consumers.getBuffer(RenderLayers.debugFilledBox());

		float x1 = (float) (box.minX - cam.x);
		float y1 = (float) (box.minY - cam.y);
		float z1 = (float) (box.minZ - cam.z);
		float x2 = (float) (box.maxX - cam.x);
		float y2 = (float) (box.maxY - cam.y);
		float z2 = (float) (box.maxZ - cam.z);

		Matrix4f matrix = matrices.peek().getPositionMatrix();

		quad(quads, matrix, color, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
		quad(quads, matrix, color, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
		quad(quads, matrix, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
		quad(quads, matrix, color, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
		quad(quads, matrix, color, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1);
		quad(quads, matrix, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix, int color,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		buffer.vertex(matrix, ax, ay, az).color(color);
		buffer.vertex(matrix, bx, by, bz).color(color);
		buffer.vertex(matrix, cx, cy, cz).color(color);
		buffer.vertex(matrix, dx, dy, dz).color(color);
	}

	private static void drawLine(MatrixStack matrices, VertexConsumerProvider consumers,
			Marker m, Vec3d cam) {
		segment(matrices, consumers, cam,
				m.x(), m.y(), m.z(), m.x2(), m.y2(), m.z2(), m.size(), m.color(), false, false);
	}

	private static void drawCircle(MatrixStack matrices, VertexConsumerProvider consumers,
			Marker m, Vec3d cam) {
		double radius = Math.max(0.05, m.radius());

		for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
			double a = i * 2 * Math.PI / CIRCLE_SEGMENTS;
			double b = (i + 1) * 2 * Math.PI / CIRCLE_SEGMENTS;

			segment(matrices, consumers, cam,
					m.x() + radius * Math.cos(a), m.y(), m.z() + radius * Math.sin(a),
					m.x() + radius * Math.cos(b), m.y(), m.z() + radius * Math.sin(b),
					m.size(), m.color(), true, true);
		}
	}

	private static void drawPath(MatrixStack matrices, VertexConsumerProvider consumers,
			Marker m, Vec3d cam) {
		List<Vec3d> points = m.points();

		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3d a = points.get(i);
			Vec3d b = points.get(i + 1);
			segment(matrices, consumers, cam, a.x, a.y, a.z, b.x, b.y, b.z, m.size(), m.color(),
					i > 0, i + 2 < points.size());
		}
	}

	private static void segment(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam,
			double x1, double y1, double z1, double x2, double y2, double z2,
			double size, int color, boolean growStart, boolean growEnd) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dz = z2 - z1;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (length < 1.0E-4) {
			return;
		}

		VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

		matrices.push();

		try {
			matrices.translate(x1 - cam.x, y1 - cam.y, z1 - cam.z);

			matrices.multiply(new Quaternionf().rotationTo(
					0.0F, 0.0F, 1.0F,
					(float) (dx / length), (float) (dy / length), (float) (dz / length)));

			double half = Math.max(0.005, size * 0.02);
			VertexRendering.drawOutline(matrices, lines,
					VoxelShapes.cuboid(new Box(-half, -half, growStart ? -half : 0.0,
							half, half, growEnd ? length + half : length)),
					0.0, 0.0, 0.0, color, 2.0F);
		} finally {
			matrices.pop();
		}
	}

	private static void drawText(MatrixStack matrices, WorldRenderContext context,
			Marker m, Vec3d cam) {
		if (m.text().isEmpty()) {
			return;
		}

		OrderedRenderCommandQueue queue = context.commandQueue();
		CameraRenderState cameraState = context.worldState() == null
				? null : context.worldState().cameraRenderState;

		if (queue == null || cameraState == null) {
			return;
		}

		double dx = m.x() - cam.x;
		double dy = m.y() - cam.y;
		double dz = m.z() - cam.z;

		matrices.push();

		try {
			matrices.translate(dx, dy, dz);

			Text text = Text.literal(m.text())
					.styled(style -> style.withColor(m.color() & 0xFFFFFF));

			queue.submitLabel(matrices, Vec3d.ZERO, 0, text, true,
					LightmapTextureManager.MAX_LIGHT_COORDINATE,
					dx * dx + dy * dy + dz * dz, cameraState);
		} finally {
			matrices.pop();
		}
	}

	private static List<Vec3d> points(com.google.gson.JsonObject data) {
		List<Vec3d> result = new java.util.ArrayList<>();

		if (!data.has("points") || !data.get("points").isJsonArray()) {
			return result;
		}

		for (var element : data.getAsJsonArray("points")) {
			if (result.size() >= MAX_PATH_POINTS) {
				InstantLore.LOGGER.warn("Polyline longer than {} points, tail dropped", MAX_PATH_POINTS);
				break;
			}

			if (!element.isJsonArray()) {
				continue;
			}

			var triple = element.getAsJsonArray();

			if (triple.size() < 3) {
				continue;
			}

			try {
				result.add(new Vec3d(triple.get(0).getAsDouble(),
						triple.get(1).getAsDouble(), triple.get(2).getAsDouble()));
			} catch (Throwable e) {
			}
		}

		return result;
	}

	private static int color(String raw) {
		String hex = raw.replace("#", "");
		try {
			long parsed = Long.parseLong(hex, 16);
			return hex.length() <= 6 ? (int) (parsed | 0xFF000000L) : (int) parsed;
		} catch (NumberFormatException e) {
			return 0xFF00E5FF;
		}
	}
}
