package com.rufana.instantlore.server.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponents;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class MermaidModule implements LoreModule {

	public static final String KIND = "mermaid";

	private static final int COMPONENT_SCAN_INTERVAL = 20;

	private static final int DROWN_THRESHOLD = -20;

	private static final float RAW_PLANT_SATURATION_MODIFIER = 0.2F;

	private static final Identifier WATER_SPEED_MODIFIER_ID =
			Identifier.of("instantlore", "mermaid_water_speed");

	private static MermaidModule instance;


	private volatile double waterEfficiency = 1.0;

	private volatile int hungerFloor = 4;

	private volatile int bottleStackSize = 16;

	private volatile boolean waterBottleRestoresAir = true;

	private volatile Set<Identifier> fishWhitelist = defaultFish();

	private volatile Set<Identifier> rawPlants = defaultRawPlants();

	private int ticks;

	public MermaidModule() {
		instance = this;
		MutantRegistry.registerKind(KIND, "Русалка");
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> resetAirOnJoin(handler.player));
	}

	private static void resetAirOnJoin(ServerPlayerEntity player) {
		if (player != null && appliesTo(player.getUuid())) {
			player.setAir(player.getMaxAir());
		}
	}

	private static Set<Identifier> defaultFish() {
		return ids(
				"minecraft:cod", "minecraft:cooked_cod",
				"minecraft:salmon", "minecraft:cooked_salmon",
				"minecraft:tropical_fish", "minecraft:pufferfish",
				"minecraft:dried_kelp");
	}

	private static Set<Identifier> defaultRawPlants() {
		return ids("minecraft:kelp", "minecraft:seagrass", "minecraft:sea_pickle");
	}

	private static Set<Identifier> ids(String... raw) {
		Set<Identifier> set = new LinkedHashSet<>();
		for (String id : raw) {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed != null) {
				set.add(parsed);
			}
		}
		return Set.copyOf(set);
	}

	@Override
	public String id() {
		return KIND;
	}

	@Override
	public String title() {
		return "Русалки (вода, море, дыхание)";
	}

	@Override
	public boolean defaultEnabled() {
		return false;
	}

	@Override
	public String describe() {
		return "скорость воды " + waterEfficiency + ", пол голода " + hungerFloor + ", "
				+ "бутылёк ×" + bottleStackSize + (waterBottleRestoresAir ? " (чинит дыхалку)" : "")
				+ ", рыбы: " + fishWhitelist.size() + ", сырьё: " + rawPlants.size()
				+ ", в реестре: " + MutantRegistry.namesWithKind(KIND).size();
	}

	@Override
	public void configure(JsonObject config) {
		this.waterEfficiency = readDouble(config, "water_efficiency", waterEfficiency, 0.0, 1.0);
		this.hungerFloor = readInt(config, "hunger_floor", hungerFloor, 0, 20);
		this.bottleStackSize = readInt(config, "bottle_stack_size", bottleStackSize, 1, 64);
		if (config.has("water_bottle_restores_air") && config.get("water_bottle_restores_air").isJsonPrimitive()) {
			this.waterBottleRestoresAir = config.get("water_bottle_restores_air").getAsBoolean();
		}
		Set<Identifier> fish = readIds(config, "fish_whitelist");
		if (fish != null) {
			this.fishWhitelist = fish;
		}
		Set<Identifier> plants = readIds(config, "raw_plants");
		if (plants != null) {
			this.rawPlants = plants;
		}
	}

	private static Set<Identifier> readIds(JsonObject config, String key) {
		if (!config.has(key) || !config.get(key).isJsonArray()) {
			return null;
		}
		Set<Identifier> set = new LinkedHashSet<>();
		for (JsonElement el : config.getAsJsonArray(key)) {
			if (el.isJsonPrimitive()) {
				Identifier parsed = Identifier.tryParse(el.getAsString());
				if (parsed != null) {
					set.add(parsed);
				}
			}
		}
		return set.isEmpty() ? null : Set.copyOf(set);
	}

	private static int readInt(JsonObject config, String key, int fallback, int min, int max) {
		if (!config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return Math.max(min, Math.min(max, config.get(key).getAsInt()));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject config, String key, double fallback, double min, double max) {
		if (!config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return Math.max(min, Math.min(max, config.get(key).getAsDouble()));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	@Override
	public void tick(MinecraftServer server) {
		ticks++;
		for (UUID uuid : MutantRegistry.uuidsWithKind(KIND)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player != null) {
				applyBreathing(player);
			}
		}
		if (ticks % COMPONENT_SCAN_INTERVAL == 0) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				maintainComponents(player, MutantRegistry.isKind(player.getUuid(), KIND));
			}
		}
	}

	@Override
	public void onDisable(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			maintainComponents(player, false);
		}
	}

	private void applyBreathing(ServerPlayerEntity player) {
		if (!(player.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		boolean inWater = player.isTouchingWater();

		if (!inWater && !world.hasRain(player.getBlockPos())) {
			int air = player.getAir();
			if (air <= DROWN_THRESHOLD) {
				player.setAir(0);
				player.damage(world, player.getDamageSources().drown(), 2.0F);
			} else if (air >= player.getMaxAir()) {
				player.setAir(player.getMaxAir() - 1);
			}
		}

		if (!player.isTouchingWater() && player.isSprinting()) {
			player.setSprinting(false);
		}
	}

	public static boolean appliesTo(UUID uuid) {
		return ModuleRegistry.isActive(KIND) && MutantRegistry.isKind(uuid, KIND);
	}

	private void maintainComponents(ServerPlayerEntity player, boolean isMermaid) {
		maintainWaterSpeed(player, isMermaid);
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (isWaterBottle(stack)) {
				maintainBottle(stack, isMermaid);
			} else if (rawPlants.contains(Registries.ITEM.getId(stack.getItem()))) {
				maintainRawPlant(stack, isMermaid);
			}
		}
	}

	private void maintainWaterSpeed(ServerPlayerEntity player, boolean isMermaid) {
		EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
		if (attr == null) {
			return;
		}
		boolean has = attr.hasModifier(WATER_SPEED_MODIFIER_ID);
		if (isMermaid && waterEfficiency > 0) {
			if (!has) {
				attr.addTemporaryModifier(new EntityAttributeModifier(
						WATER_SPEED_MODIFIER_ID, waterEfficiency, EntityAttributeModifier.Operation.ADD_VALUE));
			}
		} else if (has) {
			attr.removeModifier(WATER_SPEED_MODIFIER_ID);
		}
	}

	private void maintainBottle(ItemStack stack, boolean isMermaid) {
		Integer current = stack.get(DataComponentTypes.MAX_STACK_SIZE);
		if (isMermaid) {
			if (!Integer.valueOf(bottleStackSize).equals(current)) {
				stack.set(DataComponentTypes.MAX_STACK_SIZE, bottleStackSize);
			}
		} else if (current != null && current > 1) {
			stack.remove(DataComponentTypes.MAX_STACK_SIZE);
		}
	}

	private void maintainRawPlant(ItemStack stack, boolean isMermaid) {
		if (isMermaid) {
			if (!stack.contains(DataComponentTypes.FOOD)) {
				stack.set(DataComponentTypes.CONSUMABLE, ConsumableComponents.FOOD);
				stack.set(DataComponentTypes.FOOD, rawPlantFood());
			}
		} else if (stack.contains(DataComponentTypes.FOOD) || stack.contains(DataComponentTypes.CONSUMABLE)) {
			stack.remove(DataComponentTypes.FOOD);
			stack.remove(DataComponentTypes.CONSUMABLE);
		}
	}

	private FoodComponent rawPlantFood() {
		return new FoodComponent.Builder()
				.nutrition(hungerFloor)
				.saturationModifier(RAW_PLANT_SATURATION_MODIFIER)
				.build();
	}

	public static boolean isWaterBottle(ItemStack stack) {
		if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
			return false;
		}
		var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
		return contents != null && contents.matches(Potions.WATER);
	}


	public static boolean isAllowedFood(ItemStack stack) {
		if (instance == null) {
			return true;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		return instance.fishWhitelist.contains(id) || instance.rawPlants.contains(id);
	}

	public static void onConsumed(ServerPlayerEntity player, ItemStack snapshot) {
		if (instance == null) {
			return;
		}
		Item item = snapshot.getItem();
		if (item == Items.PUFFERFISH) {
			player.removeStatusEffect(StatusEffects.POISON);
			player.removeStatusEffect(StatusEffects.HUNGER);
			player.removeStatusEffect(StatusEffects.NAUSEA);
		}
		var food = snapshot.get(DataComponentTypes.FOOD);
		if (food != null) {
			int extra = instance.hungerFloor - food.nutrition();
			if (extra > 0) {
				player.getHungerManager().add(extra, extra * 0.1F);
			}
		}
		if (instance.waterBottleRestoresAir && isWaterBottle(snapshot)) {
			player.setAir(player.getMaxAir());
		}
	}
}
