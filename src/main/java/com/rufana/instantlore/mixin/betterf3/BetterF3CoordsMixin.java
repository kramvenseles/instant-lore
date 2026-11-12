package com.rufana.instantlore.mixin.betterf3;

import com.rufana.instantlore.client.render.LoreCoords;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(targets = "me.cominixo.betterf3.utils.DebugLine", remap = false)
public abstract class BetterF3CoordsMixin {

	private static final Set<String> INSTANTLORE$COORD_IDS = Set.of(
			"player_coords", "block_coords", "chunk_coords", "chunk_relative_coords",
			"chunk_file", "targeted_block", "targeted_fluid");

	@Shadow
	public abstract String id();

	@Inject(method = "toText", at = @At("RETURN"), cancellable = true, remap = false)
	private void instantlore$scrambleCoords(TextColor nameColor, TextColor valueColor,
			CallbackInfoReturnable<Text> cir) {
		instantlore$scrambleIfCoord(cir);
	}

	@Inject(method = "toTextCustom", at = @At("RETURN"), cancellable = true, remap = false)
	private void instantlore$scrambleCoordsCustom(TextColor nameColor,
			CallbackInfoReturnable<Text> cir) {
		instantlore$scrambleIfCoord(cir);
	}

	private void instantlore$scrambleIfCoord(CallbackInfoReturnable<Text> cir) {
		if (!LoreCoords.isScrambled() || !INSTANTLORE$COORD_IDS.contains(id())) {
			return;
		}
		Text original = cir.getReturnValue();
		if (original == null) {
			return;
		}
		cir.setReturnValue(Text.literal(LoreCoords.scrambleNumbers(original.getString())));
	}
}
