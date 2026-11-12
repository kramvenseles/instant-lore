package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerBroadcastMixin {

	@Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideBroadcast(Text message, boolean overlay, CallbackInfo ci) {
		java.util.UUID subject = InvisibleModule.hiddenBroadcastSubject(message);
		if (subject != null) {
			ci.cancel();
			InvisibleModule.relayHiddenBroadcast(subject, message);
			return;
		}
		Text scrubbed = InvisibleModule.deathHiddenKillerScrub(message);
		if (scrubbed != null) {
			ci.cancel();
			InvisibleModule.broadcastPublic(scrubbed);
		}
	}
}
