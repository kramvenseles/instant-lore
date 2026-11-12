package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.ClientTintedBlocks;
import com.rufana.instantlore.client.render.TintVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SectionBuilder.class)
public abstract class BlockRenderManagerTintMixin {
	@ModifyArgs(method = "build", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock"))
	private void instantlore$tint(Args args) {
		if (ClientTintedBlocks.isEmpty() || !(args.<Object>get(1) instanceof BlockPos pos)
				|| !ClientTintedBlocks.contains(pos.asLong())) {
			return;
		}

		VertexConsumer consumer = args.get(4);
		args.set(4, new TintVertexConsumer(consumer, ClientTintedBlocks.factor()));
	}
}
