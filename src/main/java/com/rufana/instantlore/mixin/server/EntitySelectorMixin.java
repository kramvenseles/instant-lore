package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin {

	@Shadow @Final private List<Predicate<Entity>> predicates;
	@Shadow @Final private NumberRange.DoubleRange distance;

	@Inject(method = "getPlayers", at = @At("RETURN"), cancellable = true)
	private void instantlore$filterPlayers(ServerCommandSource source,
			CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
		List<ServerPlayerEntity> filtered =
				InvisibleModule.filterSelectorPlayers(source, cir.getReturnValue());
		if (filtered != cir.getReturnValue()) {
			cir.setReturnValue(filtered);
		}
	}

	@Inject(method = "getEntities(Lnet/minecraft/server/command/ServerCommandSource;)Ljava/util/List;",
			at = @At("RETURN"), cancellable = true)
	private void instantlore$filterEntities(ServerCommandSource source,
			CallbackInfoReturnable<List<? extends Entity>> cir) {
		List<? extends Entity> filtered =
				InvisibleModule.filterSelectorEntities(source, cir.getReturnValue());
		if (filtered != cir.getReturnValue()) {
			cir.setReturnValue(filtered);
		}
	}

	@Inject(method = "getPositionPredicate", at = @At("HEAD"), cancellable = true)
	private void instantlore$cheapChecksFirst(Vec3d pos, Box box, FeatureSet features,
			CallbackInfoReturnable<Predicate<Entity>> cir) {
		boolean hasFeatures = features != null;
		boolean hasBox = box != null;
		boolean hasDistance = this.distance != null;
		if (!hasBox && !hasDistance) {
			return;
		}
		List<Predicate<Entity>> ordered = new ArrayList<>(this.predicates.size() + 3);
		if (hasDistance) {
			ordered.add(entity -> this.distance.testSqrt(entity.squaredDistanceTo(pos)));
		}
		if (hasBox) {
			ordered.add(entity -> box.intersects(entity.getBoundingBox()));
		}
		if (hasFeatures) {
			ordered.add(entity -> entity.getType().isEnabled(features));
		}
		ordered.addAll(this.predicates);
		cir.setReturnValue(Util.allOf(ordered));
	}
}
