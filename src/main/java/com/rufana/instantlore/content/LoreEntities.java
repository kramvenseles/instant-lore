package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LoreEntities {
	private static final float[] SIZE_STEPS = {0.5F, 1.0F, 2.5F, 6.0F, 12.0F, 24.0F};

	private static final EntityType<LoreMob>[] MOBS = newArray(SIZE_STEPS.length);
	private static final EntityType<LoreObject>[] OBJECTS = newArray(SIZE_STEPS.length);

	@SuppressWarnings("unchecked")
	private static <T extends net.minecraft.entity.Entity> EntityType<T>[] newArray(int size) {
		return new EntityType[size];
	}

	private LoreEntities() {
	}

	public static void register() {
		for (int step = 0; step < SIZE_STEPS.length; step++) {
			MOBS[step] = registerMob(step);
			OBJECTS[step] = registerObject(step);
		}

		InstantLore.LOGGER.info("Entity types registered: {} creatures, {} objects",
				MOBS.length, OBJECTS.length);
	}

	public static void registerAttributes() {
		for (EntityType<LoreMob> type : MOBS) {
			FabricDefaultAttributeRegistry.register(type, LoreMob.createAttributes());
		}
	}

	private static EntityType<LoreMob> registerMob(int step) {
		Identifier id = Identifier.of(InstantLore.MOD_ID, "lore_mob_" + step);
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

		EntityType<LoreMob> type = EntityType.Builder
				.<LoreMob>create(LoreMob::new, SpawnGroup.CREATURE)
				.dimensions(SIZE_STEPS[step], SIZE_STEPS[step])
				.maxTrackingRange(10)
				.build(key);

		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}

	private static EntityType<LoreObject> registerObject(int step) {
		Identifier id = Identifier.of(InstantLore.MOD_ID, "lore_object_" + step);
		RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

		EntityType<LoreObject> type = EntityType.Builder
				.<LoreObject>create(LoreObject::new, SpawnGroup.MISC)
				.dimensions(SIZE_STEPS[step], SIZE_STEPS[step])
				.maxTrackingRange(10)
				.build(key);

		return Registry.register(Registries.ENTITY_TYPE, key, type);
	}

	private static int stepFor(double size) {
		for (int step = 0; step < SIZE_STEPS.length; step++) {
			if (size <= SIZE_STEPS[step]) {
				return step;
			}
		}
		return SIZE_STEPS.length - 1;
	}

	public static EntityType<LoreMob> mobFor(double size) {
		return MOBS[stepFor(size)];
	}

	public static EntityType<LoreObject> objectFor(double size) {
		return OBJECTS[stepFor(size)];
	}

	public static EntityType<LoreMob>[] mobs() {
		return MOBS;
	}

	public static EntityType<LoreObject>[] objects() {
		return OBJECTS;
	}

	public static EntityType<?> typeFor(LoreEntityDefinition definition, boolean living) {
		return living ? mobFor(definition.extent()) : objectFor(definition.extent());
	}
}
