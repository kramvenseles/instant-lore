package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.content.LoreBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateSolidMixin {

	@Shadow
	public abstract Block getBlock();

	@Inject(method = "isSolid", at = @At("HEAD"), cancellable = true)
	private void instantlore$solidByData(CallbackInfoReturnable<Boolean> cir) {
		if (getBlock() instanceof LoreBlock block) {
			cir.setReturnValue(block.solidSupport());
		}
	}
}
