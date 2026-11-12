package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.block.TripwireBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(TripwireBlock.class)
public class TripwireEntitiesMixin {

	@ModifyVariable(method = "updatePowered(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Ljava/util/List;)V",
			at = @At("HEAD"), argsOnly = true)
	private List<? extends Entity> instantlore$filterTripwire(List<? extends Entity> entities) {
		return InvisibleModule.filterPresenceEntities(entities);
	}
}
