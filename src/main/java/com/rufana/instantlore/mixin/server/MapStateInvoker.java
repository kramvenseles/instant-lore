package com.rufana.instantlore.mixin.server;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapState.class)
public interface MapStateInvoker {

	@Invoker("hasMapInvisibilityEquipment")
	static boolean instantlore$hasMapInvisibilityEquipment(PlayerEntity player) {
		throw new AssertionError();
	}
}
