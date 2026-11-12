package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ViewerCountManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ViewerCountManager.class)
public class ChestViewerMixin {

	@Inject(method = "openContainer", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideOpen(LivingEntity entity, World world, BlockPos pos, BlockState state,
			double range, CallbackInfo ci) {
		if (InvisibleModule.chestUserHidden(entity)) {
			ci.cancel();
		}
	}

	@Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
	private void instantlore$hideClose(LivingEntity entity, World world, BlockPos pos, BlockState state,
			CallbackInfo ci) {
		if (InvisibleModule.chestUserHidden(entity)) {
			ci.cancel();
		}
	}

	@Redirect(method = "getViewingUsers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"))
	private List<Entity> instantlore$filterViewers(World world, Entity except, Box box, Predicate<? super Entity> predicate) {
		return InvisibleModule.filterChestViewers(world.getOtherEntities(except, box, predicate));
	}
}
