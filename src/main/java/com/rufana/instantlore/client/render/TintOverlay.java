package com.rufana.instantlore.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class TintOverlay {
	private static final double GROW = 0.004;

	private static final int DUST_BUDGET = 14;
	private static final double DUST_RADIUS_SQ = 40.0 * 40.0;

	private TintOverlay() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(TintOverlay::render);
		ClientTickEvents.END_CLIENT_TICK.register(TintOverlay::emitDust);
	}

	private static void render(WorldRenderContext context) {
		if (ClientTintedBlocks.isEmpty()) {
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
		int color = ClientTintedBlocks.color();

		VertexConsumer quads = consumers.getBuffer(RenderLayers.debugFilledBox());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Set<Long> tinted = ClientTintedBlocks.snapshot();

		for (long packed : tinted) {
			try {
				fillCube(quads, matrix, color, BlockPos.fromLong(packed), cam);
			} catch (Throwable e) {
			}
		}
	}

	private static void emitDust(MinecraftClient client) {
		ClientWorld world = client.world;
		PlayerEntity player = client.player;

		if (world == null || player == null || ClientTintedBlocks.isEmpty()) {
			return;
		}

		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		int budget = DUST_BUDGET;

		for (long packed : ClientTintedBlocks.snapshot()) {
			if (budget <= 0) {
				break;
			}

			if (rnd.nextFloat() > 0.05F) {
				continue;
			}

			BlockPos pos = BlockPos.fromLong(packed);
			double dx = pos.getX() + 0.5 - px;
			double dy = pos.getY() + 0.5 - py;
			double dz = pos.getZ() + 0.5 - pz;

			if (dx * dx + dy * dy + dz * dz > DUST_RADIUS_SQ) {
				continue;
			}

			world.addParticleClient(ParticleTypes.WHITE_ASH,
					pos.getX() + rnd.nextDouble(),
					pos.getY() + 1.0 + rnd.nextDouble() * 0.25,
					pos.getZ() + rnd.nextDouble(),
					0.0, 0.015, 0.0);
			budget--;
		}
	}

	private static void fillCube(VertexConsumer quads, Matrix4f matrix, int color, BlockPos pos, Vec3d cam) {
		float x1 = (float) (pos.getX() - GROW - cam.x);
		float y1 = (float) (pos.getY() - GROW - cam.y);
		float z1 = (float) (pos.getZ() - GROW - cam.z);
		float x2 = (float) (pos.getX() + 1 + GROW - cam.x);
		float y2 = (float) (pos.getY() + 1 + GROW - cam.y);
		float z2 = (float) (pos.getZ() + 1 + GROW - cam.z);

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
}
