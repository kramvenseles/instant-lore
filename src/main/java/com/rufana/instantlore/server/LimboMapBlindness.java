package com.rufana.instantlore.server;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class LimboMapBlindness {
	private static final Identifier[] EFFECT_IDS = {
			Identifier.of("xaerominimap", "no_minimap"),
			Identifier.of("xaerominimap", "no_entity_radar"),
			Identifier.of("xaerominimap", "no_waypoints"),
			Identifier.of("xaerominimap", "no_cave_maps"),
			Identifier.of("xaeroworldmap", "no_world_map"),
	};

	private static final java.util.List<RegistryEntry<EntityAttribute>> WAYPOINT_ATTRIBUTES =
			java.util.List.of(
					EntityAttributes.WAYPOINT_TRANSMIT_RANGE,
					EntityAttributes.WAYPOINT_RECEIVE_RANGE);

	private static final EntityAttributeModifier NO_WAYPOINTS = new EntityAttributeModifier(
			Identifier.of("instantlore", "limbo_no_locator"), -1.0,
			EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private LimboMapBlindness() {
	}

	public static void enforce(ServerPlayerEntity player) {
		forEach(entry -> {
			if (!player.hasStatusEffect(entry)) {
				player.addStatusEffect(new StatusEffectInstance(
						entry, StatusEffectInstance.INFINITE, 0, true, false, false));
			}
		});

		//
		forEachWaypoint(player, instance -> {
			if (!instance.hasModifier(NO_WAYPOINTS.id())) {
				instance.addTemporaryModifier(NO_WAYPOINTS);
			}
		});
	}

	public static void clear(ServerPlayerEntity player) {
		forEach(player::removeStatusEffect);
		forEachWaypoint(player, instance -> instance.removeModifier(NO_WAYPOINTS.id()));
	}

	private static void forEachWaypoint(ServerPlayerEntity player,
			java.util.function.Consumer<EntityAttributeInstance> action) {
		for (RegistryEntry<EntityAttribute> attribute : WAYPOINT_ATTRIBUTES) {
			EntityAttributeInstance instance = player.getAttributeInstance(attribute);

			if (instance != null) {
				action.accept(instance);
			}
		}
	}

	private static void forEach(java.util.function.Consumer<RegistryEntry<StatusEffect>> action) {
		for (Identifier id : EFFECT_IDS) {
			RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(id).orElse(null);
			if (entry != null) {
				action.accept(entry);
			}
		}
	}
}
