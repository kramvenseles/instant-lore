package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.MermaidModule;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConsumableComponent.class)
public class MermaidConsumeMixin {

	private static final ThreadLocal<ItemStack> instantlore$eaten = new ThreadLocal<>();

	@Inject(method = "consume", at = @At("HEAD"), cancellable = true)
	private void instantlore$onlyFish(LivingEntity user, ItemStack stack, Hand hand,
			CallbackInfoReturnable<ActionResult> cir) {
		if (user instanceof ServerPlayerEntity player && MermaidModule.appliesTo(player.getUuid())
				&& stack.contains(DataComponentTypes.FOOD) && !MermaidModule.isAllowedFood(stack)) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}

	@Inject(method = "finishConsumption", at = @At("HEAD"))
	private void instantlore$snapshot(World world, LivingEntity user, ItemStack stack,
			CallbackInfoReturnable<ItemStack> cir) {
		if (!world.isClient() && user instanceof ServerPlayerEntity player
				&& MermaidModule.appliesTo(player.getUuid())) {
			instantlore$eaten.set(stack.copy());
		}
	}

	@Inject(method = "finishConsumption", at = @At("TAIL"))
	private void instantlore$afterEat(World world, LivingEntity user, ItemStack stack,
			CallbackInfoReturnable<ItemStack> cir) {
		ItemStack snapshot = instantlore$eaten.get();
		instantlore$eaten.remove();
		if (snapshot != null && user instanceof ServerPlayerEntity player) {
			MermaidModule.onConsumed(player, snapshot);
		}
	}
}
