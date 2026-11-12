package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

@Mixin(AbstractPressurePlateBlock.class)
public class PressurePlateEntityCountMixin {

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = "getEntityCount", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getEntitiesByClass(Ljava/lang/Class;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"))
	private static List<? extends Entity> instantlore$filterPlate(World world, Class type, Box box, Predicate predicate) {
		return InvisibleModule.filterBlockEntities(world.getEntitiesByClass(type, box, predicate));
	}
}
