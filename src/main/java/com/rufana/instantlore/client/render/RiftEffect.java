package com.rufana.instantlore.client.render;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.event.LoreEventBus;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RiftEffect {
	private static final int TIERS = 26;

	private static final float FADE_END = 22.0F;

	private static final Identifier ASH_TEX = Identifier.of("instantlore", "textures/rift/ash.png");

	private static final int MAX_LOGGED_FAILURES = 5;
	private static final AtomicInteger FAILURES = new AtomicInteger();

	private static final Map<String, Rift> RIFTS = new ConcurrentHashMap<>();

	private record Rift(double x, double y, double z, float height, float width,
			float jag, int seed, int coreColor, int edgeColor, int edge2Color,
			int glowColor, float glow, float anim, float sway, int layers) {
	}

	private RiftEffect() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(RiftEffect::render);

		LoreEventBus.subscribe("rift.set", event -> {
			String id = event.string("id", null);
			if (id == null) {
				InstantLore.LOGGER.warn("rift.set without id field");
				return;
			}
			RIFTS.put(id, new Rift(
					event.number("x", 0), event.number("y", 0), event.number("z", 0),
					(float) event.number("height", 2.6),
					(float) event.number("width", 0.55),
					(float) event.number("jag", 0.35),
					(int) event.number("seed", 1337),
					color(event.string("core", "#F00A0212")),
					color(event.string("edge", "#FFC77DFF")),
					color(event.string("edge2", "#FF7A2FE0")),
					color(event.string("glow", "#FFB266FF")),
					(float) event.number("glow_w", 0.35),
					(float) event.number("anim", 1.0),
					(float) event.number("sway", 0.10),
					(int) event.number("layers", 4)));
		});

		LoreEventBus.subscribe("rift.remove", event -> {
			String id = event.string("id", null);
			if (id != null) {
				RIFTS.remove(id);
			}
		});

		LoreEventBus.subscribe("rift.clear", event -> RIFTS.clear());
	}

	public static void clear() {
		RIFTS.clear();
	}

	public static double nearestDistance(Vec3d from) {
		double best = -1.0;
		for (Rift r : RIFTS.values()) {
			double dx = r.x() - from.x, dy = r.y() - from.y, dz = r.z() - from.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (best < 0.0 || d < best) {
				best = d;
			}
		}
		return best;
	}

	private static void render(WorldRenderContext context) {
		if (RIFTS.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		MinecraftClient client = MinecraftClient.getInstance();

		if (matrices == null || consumers == null || client.gameRenderer == null) {
			return;
		}

		Camera camera = client.gameRenderer.getCamera();
		Vec3d cam = camera.getCameraPos();

		float time = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0E9);

		for (Rift rift : RIFTS.values()) {
			try {
				drawRift(matrices, consumers, rift, cam, time);
			} catch (Throwable e) {
				if (FAILURES.incrementAndGet() <= MAX_LOGGED_FAILURES) {
					InstantLore.LOGGER.error("Rift failed to render", e);
				}
			}
		}

		if (consumers instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw();
		}
	}

	private static void drawRift(MatrixStack matrices, VertexConsumerProvider consumers,
			Rift r, Vec3d cam, float time) {
		VertexConsumer buf = consumers.getBuffer(RenderLayers.debugFilledBox());
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		float cx = (float) (r.x() - cam.x);
		float cy = (float) (r.y() - cam.y);
		float cz = (float) (r.z() - cam.z);

		float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		float vis = clamp(1.0F - dist / FADE_END, 0.0F, 1.0F);
		alphaMul = vis * vis;
		if (alphaMul <= 0.0F) {
			return;
		}

		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
		Vector3f toCam = new Vector3f(-cx, 0.0F, -cz);
		if (toCam.lengthSquared() < 1.0E-6F) {
			toCam.set(0.0F, 0.0F, 1.0F);
		}
		toCam.normalize();
		Vector3f right = new Vector3f(up).cross(toCam).normalize();

		float halfH = r.height() * 0.5F;
		float glowW = Math.max(0.05F, r.glow());
		float phase = time * r.anim();

		int n = TIERS + 1;
		float[] ly = new float[n], lL = new float[n], lR = new float[n];
		int[] lcL = new int[n], lcR = new int[n];
		for (int i = 0; i < n; i++) {
			float t = i / (float) TIERS;
			float envelope = (float) Math.pow(Math.sin(Math.PI * t), 0.7);
			float half = r.width() * envelope;

			float waveL = (float) Math.sin(i * 0.7 + phase * 2.2) * r.jag() * r.width() * 0.35F;
			float waveR = (float) Math.sin(i * 0.7 + phase * 2.2 + 2.1) * r.jag() * r.width() * 0.35F;
			ly[i] = -halfH + t * r.height();
			lL[i] = -half - noise(r.seed(), i, 0) * r.jag() * r.width() - waveL;
			lR[i] = half + noise(r.seed(), i, 1) * r.jag() * r.width() + waveR;

			lcL[i] = fade(lerp(r.edgeColor(), r.edge2Color(), (noise(r.seed(), i, 2) + 1F) * 0.5F));
			lcR[i] = fade(lerp(r.edgeColor(), r.edge2Color(), (noise(r.seed(), i, 3) + 1F) * 0.5F));
		}

		int coreCol = fade(r.coreColor());
		for (int i = 1; i < n; i++) {
			quadPlanar(buf, matrix, right, up, cx, cy, cz, coreCol,
					lL[i - 1], ly[i - 1], lR[i - 1], ly[i - 1], lR[i], ly[i], lL[i], ly[i]);
		}

		ashPass(consumers, matrix, right, up, cx, cy, cz, lL, lR, ly, n, phase, 0.85F, 0.010F, 0.045F, 160);
		ashPass(consumers, matrix, right, up, cx, cy, cz, lL, lR, ly, n, phase, 0.55F, -0.008F, 0.075F, 100);

		VertexConsumer buf2 = consumers.getBuffer(RenderLayers.debugFilledBox());
		for (int i = 1; i < n; i++) {
			quadGrad(buf2, matrix, right, up, cx, cy, cz,
					lL[i - 1] - glowW, ly[i - 1], lcL[i - 1] & 0xFFFFFF, lL[i - 1], ly[i - 1], lcL[i - 1],
					lL[i], ly[i], lcL[i], lL[i] - glowW, ly[i], lcL[i] & 0xFFFFFF);
			quadGrad(buf2, matrix, right, up, cx, cy, cz,
					lR[i - 1], ly[i - 1], lcR[i - 1], lR[i - 1] + glowW, ly[i - 1], lcR[i - 1] & 0xFFFFFF,
					lR[i] + glowW, ly[i], lcR[i] & 0xFFFFFF, lR[i], ly[i], lcR[i]);
		}
	}

	private static void quadGrad(VertexConsumer buf, Matrix4f m, Vector3f right, Vector3f up,
			float cx, float cy, float cz,
			float u1, float v1, int c1, float u2, float v2, int c2,
			float u3, float v3, int c3, float u4, float v4, int c4) {
		vertexUV(buf, m, right, up, cx, cy, cz, c1, u1, v1);
		vertexUV(buf, m, right, up, cx, cy, cz, c2, u2, v2);
		vertexUV(buf, m, right, up, cx, cy, cz, c3, u3, v3);
		vertexUV(buf, m, right, up, cx, cy, cz, c4, u4, v4);
	}

	private static void ashPass(VertexConsumerProvider consumers, Matrix4f m, Vector3f right,
			Vector3f up, float cx, float cy, float cz, float[] lL, float[] lR, float[] ly, int n,
			float phase, float tile, float uSpeed, float vSpeed, int baseAlpha) {
		VertexConsumer buf = consumers.getBuffer(RenderLayers.entityTranslucent(ASH_TEX));
		Vector3f toCam = new Vector3f(right).cross(up).normalize();
		float dx = cx + toCam.x() * 0.02F, dy = cy + toCam.y() * 0.02F, dz = cz + toCam.z() * 0.02F;
		int color = fade((baseAlpha << 24) | 0xFFFFFF);
		float uOff = phase * uSpeed, vOff = phase * vSpeed;

		for (int i = 1; i < n; i++) {
			texVert(buf, m, right, up, dx, dy, dz, color, lL[i - 1], ly[i - 1], tile, uOff, vOff);
			texVert(buf, m, right, up, dx, dy, dz, color, lR[i - 1], ly[i - 1], tile, uOff, vOff);
			texVert(buf, m, right, up, dx, dy, dz, color, lR[i], ly[i], tile, uOff, vOff);
			texVert(buf, m, right, up, dx, dy, dz, color, lL[i], ly[i], tile, uOff, vOff);
		}
	}

	private static void texVert(VertexConsumer buf, Matrix4f m, Vector3f right, Vector3f up,
			float cx, float cy, float cz, int color, float u, float v, float tile,
			float uOff, float vOff) {
		float x = cx + right.x() * u + up.x() * v;
		float y = cy + right.y() * u + up.y() * v;
		float z = cz + right.z() * u + up.z() * v;
		buf.vertex(m, x, y, z).color(color).texture(u / tile + uOff, v / tile + vOff)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 0.0F, 1.0F);
	}

	private static float alphaMul = 1.0F;

	private static int fade(int c) {
		int a = Math.round(((c >>> 24) & 0xFF) * alphaMul);
		return (a << 24) | (c & 0xFFFFFF);
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static int lerp(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
		int oa = (int) (aa + (ba - aa) * t);
		int or = (int) (ar + (br - ar) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}

	private static void quadPlanar(VertexConsumer buf, Matrix4f m, Vector3f right, Vector3f up,
			float cx, float cy, float cz, int color,
			float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4) {
		vertexUV(buf, m, right, up, cx, cy, cz, color, u1, v1);
		vertexUV(buf, m, right, up, cx, cy, cz, color, u2, v2);
		vertexUV(buf, m, right, up, cx, cy, cz, color, u3, v3);
		vertexUV(buf, m, right, up, cx, cy, cz, color, u4, v4);
	}

	private static void vertexUV(VertexConsumer buf, Matrix4f m, Vector3f right, Vector3f up,
			float cx, float cy, float cz, int color, float u, float v) {
		float x = cx + right.x() * u + up.x() * v;
		float y = cy + right.y() * u + up.y() * v;
		float z = cz + right.z() * u + up.z() * v;
		buf.vertex(m, x, y, z).color(color);
	}

	private static float noise(int seed, int tier, int side) {
		int h = seed * 374761393 + tier * 668265263 + side * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 32768.0F - 1.0F;
	}

	private static int color(String raw) {
		String hex = raw.replace("#", "");
		try {
			long parsed = Long.parseLong(hex, 16);
			return hex.length() <= 6 ? (int) (parsed | 0xFF000000L) : (int) parsed;
		} catch (NumberFormatException e) {
			return 0xFFB884FF;
		}
	}
}
