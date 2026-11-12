package com.rufana.instantlore.client.render;

import com.rufana.instantlore.content.LoreBlockDefinition;
import com.rufana.instantlore.content.LoreBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class LoreBlockEntityRenderer
		implements BlockEntityRenderer<LoreBlockEntity, LoreBlockEntityRenderer.State> {

	public static class State extends BlockEntityRenderState {
		public String label = "";
		public double squaredDistance;
	}

	public LoreBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void updateRenderState(LoreBlockEntity be, State state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
		BlockEntityRenderer.super.updateRenderState(be, state, tickDelta, cameraPos, crumbling);

		LoreBlockDefinition definition = be.definition();
		state.label = definition.label() == null ? "" : definition.label();
		state.squaredDistance = cameraPos.squaredDistanceTo(
				be.getPos().getX() + 0.5, be.getPos().getY() + 0.5, be.getPos().getZ() + 0.5);
	}

	@Override
	public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		if (state.label.isEmpty()) {
			return;
		}

		queue.submitLabel(matrices,
				new Vec3d(0.5, 1.3, 0.5),
				0,
				Text.literal(state.label),
				true,
				state.lightmapCoordinates,
				state.squaredDistance,
				camera);
	}

	@Override
	public boolean rendersOutsideBoundingBox() {
		return true;
	}
}
