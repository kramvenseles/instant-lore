package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.control.ClickControl;
import com.rufana.instantlore.client.control.PlayerControl;
import com.rufana.instantlore.client.cutscene.CutscenePlayer;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerInputMixin {
	@Inject(
			method = "tickMovement",
			at = @At(value = "INVOKE", shift = At.Shift.AFTER,
					target = "Lnet/minecraft/client/input/Input;tick()V"))
	private void instantlore$overrideInput(CallbackInfo ci) {
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;

		ClickControl.endTick();

		if (CutscenePlayer.isInputLocked()) {
			instantlore$apply(self.input, PlayerInput.DEFAULT, null);
			return;
		}

		//
		if (PlayerControl.isMoveLocked() || PlayerControl.hasRawInput()) {
			PlayerInput values = PlayerControl.computeInput();
			instantlore$apply(self.input, values, PlayerControl.rawMovement());
			instantlore$jump(self, values);
		}

		float[] look = PlayerControl.computeLook();

		if (look != null) {
			self.setYaw(look[0]);
			self.setPitch(look[1]);
		}
	}

	private static void instantlore$jump(ClientPlayerEntity self, PlayerInput values) {
		if (values.jump() && self.isOnGround()) {
			self.jump();
		}
	}

	private static void instantlore$apply(Input input, PlayerInput values, Vec2f analog) {
		input.playerInput = values;

		((InputAccessor) input).instantlore$setMovementVector(analog != null
				? analog
				: new Vec2f(
						instantlore$axis(values.left(), values.right()),
						instantlore$axis(values.forward(), values.backward())));
	}

	private static float instantlore$axis(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}
		return positive ? 1.0F : -1.0F;
	}
}
