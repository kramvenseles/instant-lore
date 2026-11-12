package com.rufana.instantlore.client.script;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.LoreReports;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.ui.LoreScreen;
import com.rufana.instantlore.client.ui.LuaUi;
import com.rufana.instantlore.client.ui.ScreenActions;
import com.rufana.instantlore.client.ui.UiScreen;
import com.rufana.instantlore.content.LoreShaped;
import com.rufana.instantlore.event.LoreEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class LoreScripts {
	public static final String SCOPE = "scripts";

	private record Library(String name, String source) {
	}

	private static final java.util.List<Library> LIBRARIES = new java.util.ArrayList<>();

	public static void addLibrary(String name, byte[] source) {
		LIBRARIES.add(new Library(name, new String(source, StandardCharsets.UTF_8)));
	}

	static final ThreadLocal<Budget> INSTRUCTIONS = ThreadLocal.withInitial(Budget::new);

	static final class Budget {
		int count;
		long allocatedAtStart = -1;
	}

	private static final com.sun.management.ThreadMXBean ALLOCATION_COUNTER = allocationCounter();

	private static com.sun.management.ThreadMXBean allocationCounter() {
		if (java.lang.management.ManagementFactory.getThreadMXBean()
				instanceof com.sun.management.ThreadMXBean bean
				&& bean.isThreadAllocatedMemorySupported()) {
			bean.setThreadAllocatedMemoryEnabled(true);
			return bean;
		}

		InstantLore.LOGGER.warn("JVM does not report per-thread allocated memory - "
				+ "script memory limit disabled");
		return null;
	}

	static long allocatedBytes() {
		return ALLOCATION_COUNTER == null ? -1 : ALLOCATION_COUNTER.getCurrentThreadAllocatedBytes();
	}

	static void openBudget() {
		Budget budget = INSTRUCTIONS.get();
		budget.count = 0;
		budget.allocatedAtStart = allocatedBytes();
	}

	private LoreScripts() {
	}

	private static volatile int attempted;
	private static volatile int succeeded;

	public static int attempted() {
		return attempted;
	}

	public static int succeeded() {
		return succeeded;
	}

	private static final double REPORTS_PER_SECOND = 10.0;
	private static final double REPORT_BURST = 20.0;

	private static double reportTokens = REPORT_BURST;
	private static long reportRefillAt = System.nanoTime();
	private static long reportsDropped;

	private static final double DEFAULT_ENTITY_RADIUS = 16.0;
	private static final double MAX_ENTITY_RADIUS = 64.0;
	private static final int MAX_ENTITIES = 64;

	private static boolean allowReport() {
		long now = System.nanoTime();
		double elapsed = (now - reportRefillAt) / 1_000_000_000.0;
		reportRefillAt = now;
		reportTokens = Math.min(REPORT_BURST, reportTokens + elapsed * REPORTS_PER_SECOND);

		if (reportTokens >= 1.0) {
			reportTokens -= 1.0;
			return true;
		}

		reportsDropped++;

		if (reportsDropped % 100 == 1) {
			InstantLore.LOGGER.warn("lore.report throttled: dropped {} reports (limit {}/s)",
					reportsDropped, (int) REPORTS_PER_SECOND);
		}

		return false;
	}

	public static void reset() {
		LoreEventBus.clearScope(SCOPE);
		LoreTimers.reset();
		LIBRARIES.clear();

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen instanceof LoreScreen) {
			client.setScreen(null);
		}
		reportTokens = REPORT_BURST;
		reportRefillAt = System.nanoTime();
		reportsDropped = 0;
		attempted = 0;
		succeeded = 0;
	}

	public static void run(String name, byte[] source) {
		attempted++;

		try {
			Globals globals = LoreSandbox.create();
			globals.set("lore", loreApi(globals));

			openBudget();

			for (Library library : LIBRARIES) {
				globals.load(library.source(), library.name()).call();
			}

			globals.load(new String(source, StandardCharsets.UTF_8), name).call();
			succeeded++;
			InstantLore.LOGGER.info("Script '{}' executed", name);
		} catch (LuaError e) {
			InstantLore.LOGGER.error("Script '{}' failed: {}", name, e.getMessage());
		} catch (Throwable e) {
			InstantLore.LOGGER.error("Script '{}' failed to load", name, e);
		}
	}

	private static LuaValue loreApi(Globals globals) {
		LuaValue api = LuaValue.tableOf();

		api.set("log", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue message) {
				InstantLore.LOGGER.info("[script] {}", message.tojstring());
				return LuaValue.NIL;
			}
		});

		api.set("on", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue type, LuaValue handler) {
				if (!handler.isfunction()) {
					throw new LuaError("lore.on: вторым аргументом нужна функция");
				}

				LuaFunction function = handler.checkfunction();
				String eventType = type.checkjstring();

				LoreEventBus.subscribe(SCOPE, eventType, event -> {
					openBudget();
					function.call(LuaJson.toLua(event.data()));
				});

				return LuaValue.NIL;
			}
		});

		api.set("emit", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue type, LuaValue data) {
				String eventType = type.checkjstring();
				LoreEventBus.publish(new LoreEvent(eventType, LuaJson.toJsonObject(data)));
				return LuaValue.NIL;
			}
		});

		//
		api.set("report", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue type, LuaValue data) {
				String eventType = type.checkjstring();

				if (allowReport()) {
					LoreReports.send(eventType, LuaJson.toJsonObject(data));
				}

				return LuaValue.NIL;
			}
		});

		api.set("save", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue key, LuaValue value) {
				LoreStore.put(key.checkjstring(), value.isnil() ? null : LuaJson.toJsonValue(value));
				return LuaValue.NIL;
			}
		});

		api.set("load", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue key) {
				var stored = LoreStore.get(key.checkjstring());
				return stored == null ? LuaValue.NIL : LuaJson.toLua(stored);
			}
		});

		api.set("after", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue seconds, LuaValue handler) {
				return LoreTimers.schedule(seconds.checkdouble(), requireFunction("after", handler), false);
			}
		});

		api.set("every", new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue seconds, LuaValue handler) {
				return LoreTimers.schedule(seconds.checkdouble(), requireFunction("every", handler), true);
			}
		});

		api.set("player", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				MinecraftClient client = MinecraftClient.getInstance();
				ClientPlayerEntity player = client.player;

				if (player == null) {
					return LuaValue.NIL;
				}

				LuaTable state = new LuaTable();
				state.set("x", LuaValue.valueOf(player.getX()));
				state.set("y", LuaValue.valueOf(player.getY()));
				state.set("z", LuaValue.valueOf(player.getZ()));
				state.set("yaw", LuaValue.valueOf(player.getYaw()));
				state.set("pitch", LuaValue.valueOf(player.getPitch()));

				state.set("name", LuaValue.valueOf(player.getName().getString()));
				state.set("health", LuaValue.valueOf(player.getHealth()));
				state.set("max_health", LuaValue.valueOf(player.getMaxHealth()));
				state.set("food", LuaValue.valueOf(player.getHungerManager().getFoodLevel()));
				state.set("saturation", LuaValue.valueOf(player.getHungerManager().getSaturationLevel()));
				state.set("air", LuaValue.valueOf(player.getAir()));
				state.set("max_air", LuaValue.valueOf(player.getMaxAir()));
				state.set("xp_level", LuaValue.valueOf(player.experienceLevel));
				state.set("xp_progress", LuaValue.valueOf(player.experienceProgress));

				state.set("on_ground", LuaValue.valueOf(player.isOnGround()));
				state.set("in_water", LuaValue.valueOf(player.isTouchingWater()));
				state.set("in_lava", LuaValue.valueOf(player.isInLava()));
				state.set("submerged", LuaValue.valueOf(player.isSubmergedInWater()));
				state.set("sneaking", LuaValue.valueOf(player.isSneaking()));
				state.set("sprinting", LuaValue.valueOf(player.isSprinting()));
				state.set("burning", LuaValue.valueOf(player.isOnFire()));
				state.set("sleeping", LuaValue.valueOf(player.isSleeping()));
				state.set("riding", LuaValue.valueOf(player.hasVehicle()));
				state.set("gliding", LuaValue.valueOf(player.isGliding()));
				state.set("powder_snow", LuaValue.valueOf(player.isFrozen()));

				Vec3d velocity = player.getVelocity();
				state.set("vx", LuaValue.valueOf(velocity.x));
				state.set("vy", LuaValue.valueOf(velocity.y));
				state.set("vz", LuaValue.valueOf(velocity.z));

				if (client.interactionManager != null) {
					state.set("gamemode", LuaValue.valueOf(client.interactionManager.getCurrentGameMode().asString()));
				}

				if (client.world != null) {
					state.set("time", LuaValue.valueOf(client.world.getTimeOfDay()));
					state.set("dimension", LuaValue.valueOf(client.world.getRegistryKey().getValue().toString()));
				}

				state.set("held", itemTable(player.getMainHandStack()));
				state.set("offhand", itemTable(player.getOffHandStack()));

				LuaTable armor = new LuaTable();
				armor.set("helmet", itemTable(player.getEquippedStack(EquipmentSlot.HEAD)));
				armor.set("chestplate", itemTable(player.getEquippedStack(EquipmentSlot.CHEST)));
				armor.set("leggings", itemTable(player.getEquippedStack(EquipmentSlot.LEGS)));
				armor.set("boots", itemTable(player.getEquippedStack(EquipmentSlot.FEET)));
				state.set("armor", armor);

				if (player.isUsingItem()) {
					LuaTable using = new LuaTable();
					using.set("item", itemTable(player.getActiveItem()));
					using.set("hand", LuaValue.valueOf(
							player.getActiveHand() == Hand.MAIN_HAND ? "main" : "off"));
					state.set("using_item", using);
				} else {
					state.set("using_item", LuaValue.NIL);
				}

				LuaTable effects = new LuaTable();
				int i = 0;
				for (StatusEffectInstance instance : player.getStatusEffects()) {
					LuaTable one = new LuaTable();
					one.set("id", LuaValue.valueOf(instance.getEffectType().getKey()
							.map(key -> key.getValue().toString()).orElse("unknown")));
					one.set("amplifier", LuaValue.valueOf(instance.getAmplifier()));
					one.set("duration", LuaValue.valueOf(instance.getDuration()));
					effects.set(++i, one);
				}
				state.set("effects", effects);

				return state;
			}
		});

		api.set("inventory", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				ClientPlayerEntity player = MinecraftClient.getInstance().player;

				if (player == null) {
					return LuaValue.NIL;
				}

				var inventory = player.getInventory();
				LuaTable list = new LuaTable();
				int count = 0;

				for (int slot = 0; slot < inventory.size(); slot++) {
					ItemStack stack = inventory.getStack(slot);

					if (stack.isEmpty()) {
						continue;
					}

					LuaTable one = new LuaTable();
					one.set("slot", LuaValue.valueOf(slot));
					one.set("id", LuaValue.valueOf(Registries.ITEM.getId(stack.getItem()).toString()));
					one.set("count", LuaValue.valueOf(stack.getCount()));
					one.set("name", LuaValue.valueOf(stack.getName().getString()));
					list.set(++count, one);
				}

				return list;
			}
		});

		api.set("world", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				MinecraftClient client = MinecraftClient.getInstance();
				ClientWorld world = client.world;

				if (world == null) {
					return LuaValue.NIL;
				}

				long time = world.getTimeOfDay();
				long dayTime = ((time % 24000L) + 24000L) % 24000L;

				LuaTable state = new LuaTable();
				state.set("time", LuaValue.valueOf(time));
				state.set("day_time", LuaValue.valueOf(dayTime));
				state.set("day", LuaValue.valueOf(time / 24000L));
				state.set("is_day", LuaValue.valueOf(dayTime < 12000L));
				state.set("is_night", LuaValue.valueOf(dayTime >= 12000L));
				state.set("raining", LuaValue.valueOf(world.isRaining()));
				state.set("thundering", LuaValue.valueOf(world.isThundering()));
				state.set("moon_phase", LuaValue.valueOf((int) ((time / 24000L) % 8L)));
				state.set("dimension", LuaValue.valueOf(world.getRegistryKey().getValue().toString()));

				ClientPlayerEntity player = client.player;
				if (player != null) {
					BlockPos pos = player.getBlockPos();
					state.set("biome", LuaValue.valueOf(world.getBiome(pos).getKey()
							.map(key -> key.getValue().toString()).orElse("unknown")));
					state.set("light", LuaValue.valueOf(world.getLightLevel(pos)));
					state.set("block_light", LuaValue.valueOf(world.getLightLevel(LightType.BLOCK, pos)));
					state.set("sky_light", LuaValue.valueOf(world.getLightLevel(LightType.SKY, pos)));
				}

				return state;
			}
		});

		api.set("block", new ThreeArgFunction() {
			@Override
			public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
				ClientWorld world = MinecraftClient.getInstance().world;

				if (world == null) {
					return LuaValue.NIL;
				}

				BlockPos pos = BlockPos.ofFloored(x.checkdouble(), y.checkdouble(), z.checkdouble());
				BlockState blockState = world.getBlockState(pos);

				LuaTable result = new LuaTable();
				result.set("id", LuaValue.valueOf(Registries.BLOCK.getId(blockState.getBlock()).toString()));
				result.set("air", LuaValue.valueOf(blockState.isAir()));
				result.set("solid", LuaValue.valueOf(!blockState.getCollisionShape(world, pos).isEmpty()));
				result.set("x", LuaValue.valueOf(pos.getX()));
				result.set("y", LuaValue.valueOf(pos.getY()));
				result.set("z", LuaValue.valueOf(pos.getZ()));
				return result;
			}
		});

		//
		api.set("entities", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				MinecraftClient client = MinecraftClient.getInstance();
				ClientWorld world = client.world;
				ClientPlayerEntity player = client.player;

				if (world == null || player == null) {
					return LuaValue.NIL;
				}

				double radius = args.narg() >= 1 ? args.checkdouble(1) : DEFAULT_ENTITY_RADIUS;
				radius = Math.max(1.0, Math.min(radius, MAX_ENTITY_RADIUS));

				Box box = player.getBoundingBox().expand(radius);
				LuaTable list = new LuaTable();
				int count = 0;

				for (Entity entity : world.getOtherEntities(player, box)) {
					if (count >= MAX_ENTITIES) {
						break;
					}

					LuaTable one = new LuaTable();
					one.set("type", LuaValue.valueOf(Registries.ENTITY_TYPE.getId(entity.getType()).toString()));
					one.set("x", LuaValue.valueOf(entity.getX()));
					one.set("y", LuaValue.valueOf(entity.getY()));
					one.set("z", LuaValue.valueOf(entity.getZ()));
					one.set("distance", LuaValue.valueOf(Math.sqrt(entity.squaredDistanceTo(player))));
					one.set("yaw", LuaValue.valueOf(entity.getYaw()));

					if (entity instanceof LoreShaped shaped && shaped.loreSlot() >= 0) {
						one.set("slot", LuaValue.valueOf(shaped.loreSlot()));
					}

					if (entity instanceof LivingEntity living) {
						one.set("health", LuaValue.valueOf(living.getHealth()));
						one.set("max_health", LuaValue.valueOf(living.getMaxHealth()));
					}

					if (entity instanceof PlayerEntity otherPlayer) {
						one.set("name", LuaValue.valueOf(otherPlayer.getName().getString()));
					}

					list.set(++count, one);
				}

				return list;
			}
		});

		//
		api.set("target", new ZeroArgFunction() {
			@Override
			public LuaValue call() {
				MinecraftClient client = MinecraftClient.getInstance();
				ClientPlayerEntity player = client.player;
				HitResult hit = client.crosshairTarget;

				if (player == null || hit == null || hit.getType() == HitResult.Type.MISS) {
					return LuaValue.NIL;
				}

				LuaTable result = new LuaTable();
				Vec3d pos = hit.getPos();
				result.set("x", LuaValue.valueOf(pos.x));
				result.set("y", LuaValue.valueOf(pos.y));
				result.set("z", LuaValue.valueOf(pos.z));
				result.set("distance", LuaValue.valueOf(Math.sqrt(hit.squaredDistanceTo(player))));

				if (hit instanceof BlockHitResult blockHit) {
					BlockPos blockPos = blockHit.getBlockPos();
					result.set("type", LuaValue.valueOf("block"));
					result.set("block_x", LuaValue.valueOf(blockPos.getX()));
					result.set("block_y", LuaValue.valueOf(blockPos.getY()));
					result.set("block_z", LuaValue.valueOf(blockPos.getZ()));
					result.set("side", LuaValue.valueOf(blockHit.getSide().asString()));

					if (client.world != null) {
						BlockState state = client.world.getBlockState(blockPos);
						result.set("id", LuaValue.valueOf(Registries.BLOCK.getId(state.getBlock()).toString()));
					}
				} else if (hit instanceof EntityHitResult entityHit) {
					Entity entity = entityHit.getEntity();
					result.set("type", LuaValue.valueOf("entity"));
					result.set("entity_type", LuaValue.valueOf(
							Registries.ENTITY_TYPE.getId(entity.getType()).toString()));

					if (entity instanceof LoreShaped shaped && shaped.loreSlot() >= 0) {
						result.set("slot", LuaValue.valueOf(shaped.loreSlot()));
					}
				}

				return result;
			}
		});

		//
		api.set("visible", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				ClientWorld world = MinecraftClient.getInstance().world;

				if (world == null || args.narg() < 6) {
					return LuaValue.NIL;
				}

				Vec3d from = new Vec3d(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
				Vec3d to = new Vec3d(args.checkdouble(4), args.checkdouble(5), args.checkdouble(6));

				BlockHitResult hit = world.raycast(new RaycastContext(
						from, to,
						RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE,
						ShapeContext.absent()));

				LuaTable result = new LuaTable();
				boolean clear = hit.getType() == HitResult.Type.MISS;
				result.set("clear", LuaValue.valueOf(clear));

				if (!clear) {
					Vec3d at = hit.getPos();
					BlockPos blockPos = hit.getBlockPos();
					result.set("x", LuaValue.valueOf(at.x));
					result.set("y", LuaValue.valueOf(at.y));
					result.set("z", LuaValue.valueOf(at.z));
					result.set("distance", LuaValue.valueOf(from.distanceTo(at)));
					result.set("block_x", LuaValue.valueOf(blockPos.getX()));
					result.set("block_y", LuaValue.valueOf(blockPos.getY()));
					result.set("block_z", LuaValue.valueOf(blockPos.getZ()));
					result.set("id", LuaValue.valueOf(
							Registries.BLOCK.getId(world.getBlockState(blockPos).getBlock()).toString()));
				}

				return result;
			}
		});

		api.set("screen", new OneArgFunction() {
			@Override
			public LuaValue call(LuaValue spec) {
				if (!spec.istable()) {
					throw new LuaError("lore.screen: нужна таблица");
				}

				Map<String, LuaFunction> callbacks = new HashMap<>();
				UiScreen definition = LuaUi.toScreen(spec.checktable(), callbacks);
				LuaValue onClose = spec.get("on_close");
				LuaValue onScroll = spec.get("on_scroll");
				LuaValue onDrag = spec.get("on_drag");
				LuaValue onRelease = spec.get("on_release");

				ScreenActions actions = new ScreenActions() {
					@Override
					public void onAction(String action, Map<String, String> inputs, int cx, int cy) {
						LuaFunction fn = callbacks.get(action);
						if (fn != null) {
							openBudget();
							fn.call(inputsToLua(inputs), clickToLua(cx, cy));
						}
					}

					@Override
					public void onClosed() {
						if (onClose.isfunction()) {
							openBudget();
							onClose.call();
						}
					}

					@Override
					public void onScroll(int mx, int my, int dir) {
						if (onScroll.isfunction()) {
							openBudget();
							onScroll.call(scrollToLua(mx, my, dir));
						}
					}

					@Override
					public void onDrag(int mx, int my, int dx, int dy) {
						if (onDrag.isfunction()) {
							openBudget();
							onDrag.call(dragToLua(mx, my, dx, dy));
						}
					}

					@Override
					public void onRelease(int mx, int my) {
						if (onRelease.isfunction()) {
							openBudget();
							onRelease.call(pointToLua(mx, my));
						}
					}
				};

				MinecraftClient client = MinecraftClient.getInstance();
				if (client.world != null) {
					client.setScreen(new LoreScreen("@script", definition, Map.of(), actions));
				}

				LuaTable handle = new LuaTable();
				handle.set("close", new ZeroArgFunction() {
					@Override
					public LuaValue call() {
						MinecraftClient client = MinecraftClient.getInstance();
						if (client.currentScreen instanceof LoreScreen) {
							client.setScreen(null);
						}
						return LuaValue.NIL;
					}
				});
				return handle;
			}
		});

		return api;
	}

	private static LuaValue inputsToLua(Map<String, String> inputs) {
		LuaTable table = new LuaTable();
		inputs.forEach((key, value) -> table.set(key, LuaValue.valueOf(value)));
		return table;
	}

	private static LuaValue pointToLua(int x, int y) {
		LuaTable table = new LuaTable();
		table.set("x", LuaValue.valueOf(x));
		table.set("y", LuaValue.valueOf(y));
		return table;
	}

	private static LuaValue dragToLua(int mx, int my, int dx, int dy) {
		LuaTable table = new LuaTable();
		table.set("x", LuaValue.valueOf(mx));
		table.set("y", LuaValue.valueOf(my));
		table.set("dx", LuaValue.valueOf(dx));
		table.set("dy", LuaValue.valueOf(dy));
		return table;
	}

	private static LuaValue scrollToLua(int mx, int my, int dir) {
		LuaTable table = new LuaTable();
		table.set("x", LuaValue.valueOf(mx));
		table.set("y", LuaValue.valueOf(my));
		table.set("dir", LuaValue.valueOf(dir));
		return table;
	}

	private static LuaValue clickToLua(int cx, int cy) {
		if (cx < 0 || cy < 0) {
			return LuaValue.NIL;
		}
		LuaTable table = new LuaTable();
		table.set("x", LuaValue.valueOf(cx));
		table.set("y", LuaValue.valueOf(cy));
		return table;
	}

	private static LuaValue itemTable(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return LuaValue.NIL;
		}

		LuaTable table = new LuaTable();
		table.set("id", LuaValue.valueOf(Registries.ITEM.getId(stack.getItem()).toString()));
		table.set("count", LuaValue.valueOf(stack.getCount()));
		table.set("name", LuaValue.valueOf(stack.getName().getString()));

		if (stack.isDamageable()) {
			table.set("damage", LuaValue.valueOf(stack.getDamage()));
			table.set("max_damage", LuaValue.valueOf(stack.getMaxDamage()));
		}

		if (stack.hasEnchantments()) {
			LuaTable enchantments = new LuaTable();
			int i = 0;
			for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
				LuaTable one = new LuaTable();
				one.set("id", LuaValue.valueOf(entry.getKey().getKey()
						.map(key -> key.getValue().toString()).orElse("unknown")));
				one.set("level", LuaValue.valueOf(entry.getIntValue()));
				enchantments.set(++i, one);
			}
			table.set("enchantments", enchantments);
		}

		return table;
	}

	private static LuaFunction requireFunction(String where, LuaValue handler) {
		if (!handler.isfunction()) {
			throw new LuaError("lore." + where + ": вторым аргументом нужна функция");
		}

		return handler.checkfunction();
	}
}
