package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.LimboTransfer;
import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapState.class)
public class MapStateMixin {

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void instantlore$blankInLimbo(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (player.getEntityWorld().getRegistryKey().equals(LimboTransfer.LIMBO_KEY)) {
			ci.cancel();
		}
	}

	@Redirect(method = "update", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/item/map/MapState;hasMapInvisibilityEquipment(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
	private boolean instantlore$hideVanished(PlayerEntity player) {
		return MapStateInvoker.instantlore$hasMapInvisibilityEquipment(player)
				|| InvisibleModule.hiddenFromMaps(player);
	}
}
