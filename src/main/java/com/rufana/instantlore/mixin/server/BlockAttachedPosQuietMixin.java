package com.rufana.instantlore.mixin.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedPosQuietMixin {

	@Shadow
	protected BlockPos attachedBlockPos;

	@Redirect(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V",
			at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V"))
	private void instantlore$fixAttachedPosQuietly(Logger logger, String message, Object pos) {
		this.attachedBlockPos = ((Entity) (Object) this).getBlockPos();
	}
}
