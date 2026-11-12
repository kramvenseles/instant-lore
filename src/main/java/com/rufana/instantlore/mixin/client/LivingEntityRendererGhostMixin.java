package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.ClientGhosts;
import com.rufana.instantlore.client.GhostFlag;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererGhostMixin {

	@Shadow
	public abstract Identifier getTexture(LivingEntityRenderState state);

	@Shadow
	public abstract EntityModel<?> getModel();

	private static final int GHOST_ALPHA = 0x66;

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void instantlore$markGhost(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		boolean ghost = entity instanceof PlayerEntity && ClientGhosts.contains(entity.getUuid());
		((GhostFlag) state).instantlore$setGhost(ghost);
	}

	@Inject(method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
			at = @At("HEAD"), cancellable = true)
	private void instantlore$ghostAlpha(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		if (((GhostFlag) state).instantlore$isGhost()) {
			cir.setReturnValue((GHOST_ALPHA << 24) | 0xFFFFFF);
		}
	}

	@Inject(method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
			at = @At("HEAD"), cancellable = true)
	private void instantlore$ghostLayer(LivingEntityRenderState state, boolean visible, boolean translucent,
			boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
		if (visible && ((GhostFlag) state).instantlore$isGhost()) {
			cir.setReturnValue(RenderLayers.entityTranslucent(getTexture(state)));
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("HEAD"))
	private void instantlore$hideLegs(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera, CallbackInfo ci) {
		if (((GhostFlag) state).instantlore$isGhost() && getModel() instanceof BipedEntityModel<?> biped) {
			biped.leftLeg.visible = false;
			biped.rightLeg.visible = false;
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
			at = @At("TAIL"))
	private void instantlore$restoreLegs(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera, CallbackInfo ci) {
		if (getModel() instanceof BipedEntityModel<?> biped) {
			biped.leftLeg.visible = true;
			biped.rightLeg.visible = true;
		}
	}
}
