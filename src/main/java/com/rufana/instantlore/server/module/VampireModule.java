package com.rufana.instantlore.server.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class VampireModule implements LoreModule {

	private static final int TICK_INTERVAL = 20;


	private volatile int effectDurationTicks = 60;

	private volatile int fireSeconds = 4;

	private volatile Set<String> hatNames = Set.of("Farmer's hat");

	private volatile boolean rainExtinguishes = true;

	private int ticks;

	public static final String KIND = "vampire";

	public VampireModule() {
		MutantRegistry.registerKind(KIND, "Вампир");
	}

	@Override
	public String id() {
		return "vampire";
	}

	@Override
	public String title() {
		return "Вампиры (наказание солнцем)";
	}

	@Override
	public boolean defaultEnabled() {
		return false;
	}

	@Override
	public String describe() {
		return "эффекты " + effectDurationTicks + " тиков, горит " + fireSeconds + " с, шляпы "
				+ hatNames + ", дождь тушит: " + (rainExtinguishes ? "да" : "нет")
				+ ", в реестре: " + MutantRegistry.namesWithKind(KIND).size();
	}

	@Override
	public void configure(JsonObject config) {
		this.effectDurationTicks = readInt(config, "effect_duration_ticks", effectDurationTicks, 20, 2400);
		this.fireSeconds = readInt(config, "fire_seconds", fireSeconds, 0, 60);
		Set<String> names = new LinkedHashSet<>();
		if (config.has("farmer_hat_name") && config.get("farmer_hat_name").isJsonPrimitive()) {
			names.add(config.get("farmer_hat_name").getAsString());
		}
		if (config.has("hat_names") && config.get("hat_names").isJsonArray()) {
			for (JsonElement element : config.getAsJsonArray("hat_names")) {
				if (element.isJsonPrimitive()) {
					names.add(element.getAsString());
				}
			}
		}
		if (!names.isEmpty()) {
			this.hatNames = Set.copyOf(names);
		}
		if (config.has("rain_extinguishes") && config.get("rain_extinguishes").isJsonPrimitive()) {
			this.rainExtinguishes = config.get("rain_extinguishes").getAsBoolean();
		}
	}

	private static int readInt(JsonObject config, String key, int fallback, int min, int max) {
		if (!config.has(key) || !config.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			int value = config.get(key).getAsInt();
			return Math.max(min, Math.min(max, value));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	@Override
	public void tick(MinecraftServer server) {
		if (++ticks < TICK_INTERVAL) {
			return;
		}
		ticks = 0;

		for (UUID uuid : MutantRegistry.uuidsWithKind(KIND)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player == null) {
				continue;
			}
			apply(player);
		}
	}

	private void apply(ServerPlayerEntity player) {
		if (!(player.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		if (!world.isDay()) {
			return;
		}
		if (rainExtinguishes && (world.isRaining() || world.isThundering())) {
			return;
		}

		BlockPos pos = player.getBlockPos();
		if (!world.isSkyVisible(pos)) {
			return;
		}
		if (player.isTouchingWater() || player.isSubmergedInWater()) {
			return;
		}

		ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
		if (isImmuneHat(head)) {
			return;
		}

		if (isFullArmorSet(player)) {
			player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.WEAKNESS, effectDurationTicks, 0, false, false, true));
			player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.SLOWNESS, effectDurationTicks, 0, false, false, true));
			return;
		}

		player.setOnFireFor(fireSeconds);
	}

	private boolean isImmuneHat(ItemStack stack) {
		if (stack.isEmpty() || !stack.isOf(Items.CARVED_PUMPKIN)) {
			return false;
		}
		Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
		return name != null && hatNames.contains(name.getString());
	}

	private static boolean isFullArmorSet(ServerPlayerEntity player) {
		ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
		if (head.isEmpty() || head.isOf(Items.CARVED_PUMPKIN)) {
			return false;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getEquippedStack(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}


	public static boolean add(ServerPlayerEntity player) {
		return MutantRegistry.add(player, KIND);
	}

	public static boolean isVampire(UUID uuid) {
		return MutantRegistry.isKind(uuid, KIND);
	}
}
