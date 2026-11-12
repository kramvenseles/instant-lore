package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.AltarPlaceFix;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At("RETURN"))
	private void instantlore$altarExactPlace(ItemPlacementContext context,
			CallbackInfoReturnable<ActionResult> cir) {
		if (cir.getReturnValue().isAccepted()) {
			AltarPlaceFix.onPlaced(context);
		}
	}
}
