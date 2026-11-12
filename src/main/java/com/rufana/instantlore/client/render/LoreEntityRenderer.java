package com.rufana.instantlore.client.render;

import com.rufana.instantlore.content.LoreBlocks;
import com.rufana.instantlore.content.LoreEntityDefinition;
import com.rufana.instantlore.content.LoreShaped;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class LoreEntityRenderer<T extends Entity & LoreShaped>
		extends EntityRenderer<T, LoreEntityRenderer.State> {

	public static class State extends EntityRenderState {
		public BlockState block = null;
		public float scale = 1.0F;
		public float yaw;
		public float pitch;
		public float roll;
	}

	public LoreEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void updateRenderState(T entity, State state, float tickDelta) {
		super.updateRenderState(entity, state, tickDelta);

		LoreEntityDefinition definition = entity.loreDefinition();

		state.block = entity.loreSlot() < 0 ? null : LoreBlocks.get(entity.appearanceBlockSlot()).currentState();
		state.scale = (float) definition.scale();

		//
		//
		if (state.block != null) {
			int luminance = state.block.getLuminance();
			if (luminance > 0) {
				int sky = net.minecraft.client.render.LightmapTextureManager.getSkyLightCoordinates(state.light);
				int blockLight = Math.max(
						net.minecraft.client.render.LightmapTextureManager.getBlockLightCoordinates(state.light),
						luminance);
				state.light = net.minecraft.client.render.LightmapTextureManager.pack(blockLight, sky);
			}
		}

		//
		state.yaw = entity instanceof LivingEntity living
				? MathHelper.lerpAngleDegrees(tickDelta, living.lastHeadYaw, living.headYaw)
				: MathHelper.lerpAngleDegrees(tickDelta, entity.lastYaw, entity.getYaw());

		state.pitch = entity.renderPitch();
		state.roll = entity.renderRoll();
	}

	@Override
	public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.block != null) {
			matrices.push();

			matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-state.yaw));

			if (state.pitch != 0.0F) {
				matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
			}
			if (state.roll != 0.0F) {
				matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));
			}

			matrices.scale(state.scale, state.scale, state.scale);
			matrices.translate(-0.5F, 0.0F, -0.5F);

			queue.submitBlock(matrices, state.block, state.light,
					OverlayTexture.DEFAULT_UV, state.outlineColor);

			matrices.pop();
		}

		super.render(state, matrices, queue, camera);
	}
}
