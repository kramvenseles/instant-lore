package com.rufana.instantlore.mixin.client;

import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(StatusEffectsDisplay.class)
public abstract class EffectScreenHideMapBlindnessMixin {

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;getStatusEffects()Ljava/util/Collection;"))
	private Collection<StatusEffectInstance> instantlore$hideMapBlindness(ClientPlayerEntity player) {
		Collection<StatusEffectInstance> effects = player.getStatusEffects();

		if (effects == null || effects.isEmpty()) {
			return effects;
		}

		List<StatusEffectInstance> visible = new ArrayList<>(effects.size());

		for (StatusEffectInstance instance : effects) {
			if (!isMapBlindness(instance)) {
				visible.add(instance);
			}
		}

		return visible;
	}

	private static boolean isMapBlindness(StatusEffectInstance instance) {
		Identifier id = instance.getEffectType().getKey().map(key -> key.getValue()).orElse(null);
		return id != null
				&& (id.getNamespace().equals("xaerominimap") || id.getNamespace().equals("xaeroworldmap"));
	}
}
