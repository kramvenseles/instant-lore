package com.rufana.instantlore.mixin.graves;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "eu.pb4.graves.other.GraveUtils", remap = false)
public class GraveUtilsMixin {

	@ModifyArg(
			method = "createGrave",
			at = @At(value = "INVOKE",
					target = "Leu/pb4/graves/grave/Grave;createBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/text/Text;Ljava/util/Collection;Ljava/util/Collection;I)Leu/pb4/graves/grave/Grave;"),
			index = 4,
			remap = true)
	private static Text instantlore$scrubVanishedKiller(Text deathCause) {
		Text scrubbed = InvisibleModule.deathHiddenKillerScrub(deathCause);
		return scrubbed != null ? scrubbed : deathCause;
	}
}
