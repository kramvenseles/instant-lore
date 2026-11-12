package com.rufana.instantlore.mixin.server;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.rule.GameRule;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDeathMessageMixin {

	private static final Set<String> STELLARITY_FLAVORED = Set.of(
			"stellarity.damage.carcanet",
			"stellarity.damage.dot.frostburn",
			"stellarity.damage.dot.prismaric_inferno",
			"stellarity.damage.dragonblade",
			"stellarity.damage.empress_of_light.dash",
			"stellarity.damage.empress_of_light.ethereal_lance",
			"stellarity.damage.empress_of_light.prismatic_bolts",
			"stellarity.damage.example_cause",
			"stellarity.damage.kaleidoscope",
			"stellarity.damage.sharanga_explosion",
			"stellarity.damage.spirit_dagger",
			"stellarity.damage.tamaris");

	@Redirect(method = "onDeath", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/rule/GameRules;getValue(Lnet/minecraft/world/rule/GameRule;)Ljava/lang/Object;"))
	private Object instantlore$deathMessageThroughStellarity(GameRules rules, GameRule<?> rule) {
		Object value = rules.getValue(rule);
		if (rule != GameRules.SHOW_DEATH_MESSAGES) {
			return value;
		}
		for (String tag : ((Entity) (Object) this).getCommandTags()) {
			if (STELLARITY_FLAVORED.contains(tag)) {
				return Boolean.FALSE;
			}
		}
		return Boolean.TRUE;
	}
}
