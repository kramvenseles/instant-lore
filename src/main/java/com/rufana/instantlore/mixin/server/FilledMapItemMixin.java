package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.LimboTransfer;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FilledMapItem.class)
public class FilledMapItemMixin {

	@Inject(method = "updateColors", at = @At("HEAD"), cancellable = true)
	private void instantlore$noScanInLimbo(World world, Entity entity, MapState state, CallbackInfo ci) {
		if (world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY)) {
			ci.cancel();
		}
	}
}
