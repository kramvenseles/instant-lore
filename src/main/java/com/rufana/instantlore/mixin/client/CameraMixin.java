package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.cutscene.CutscenePlayer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@org.spongepowered.asm.mixin.Shadow
	protected abstract void setPos(Vec3d pos);

	@org.spongepowered.asm.mixin.Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@org.spongepowered.asm.mixin.Shadow
	public abstract float getYaw();

	@org.spongepowered.asm.mixin.Shadow
	public abstract float getPitch();

	@org.spongepowered.asm.mixin.Shadow
	@org.spongepowered.asm.mixin.Final
	private org.joml.Quaternionf rotation;

	@Inject(method = "update", at = @At("RETURN"))
	private void instantlore$overrideDuringCutscene(World area, Entity focused, boolean thirdPerson,
			boolean inverseView, float tickDelta, CallbackInfo ci) {
		instantlore$applyCutscene();

		float[] shake = com.rufana.instantlore.client.camera.CameraEffects.shakeOffset();

		if (shake != null) {
			setRotation(getYaw() + shake[0], getPitch() + shake[1]);
		}

		//
		float roll = CutscenePlayer.cameraRoll();

		if (roll != 0.0F) {
			this.rotation.rotateZ((float) Math.toRadians(-roll));
		}
	}

	private void instantlore$applyCutscene() {
		if (!CutscenePlayer.isActive()) {
			return;
		}

		Vec3d position = CutscenePlayer.cameraPos();

		if (position == null) {
			return;
		}

		float[] rotation = CutscenePlayer.cameraRotation();

		if (rotation != null) {
			setRotation(rotation[0], rotation[1]);
		}

		setPos(position);
	}
}
