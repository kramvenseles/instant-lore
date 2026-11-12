package com.rufana.instantlore;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rufana.instantlore.content.LoreBlockDefinition;
import com.rufana.instantlore.content.LoreBlockEntities;
import com.rufana.instantlore.content.LoreBlocks;
import com.rufana.instantlore.content.LoreEntities;
import com.rufana.instantlore.content.LoreEntityDefinition;
import com.rufana.instantlore.content.LoreMob;
import com.rufana.instantlore.content.LoreObject;
import com.rufana.instantlore.content.LoreShaped;
import com.rufana.instantlore.content.LoreContent;
import com.rufana.instantlore.content.LoreItemDefinition;
import com.rufana.instantlore.content.LoreItemPool;
import com.rufana.instantlore.server.SoulMoney;
import com.rufana.instantlore.content.LoreItems;
import com.rufana.instantlore.content.ServerContentLoader;
import com.rufana.instantlore.content.ServerContentState;
import com.rufana.instantlore.net.ContentSources;
import com.rufana.instantlore.net.ContentVersionPayload;
import com.rufana.instantlore.net.LoreEventPayload;
import com.rufana.instantlore.net.LoreHealthPayload;
import com.rufana.instantlore.net.LoreModsPayload;
import com.rufana.instantlore.net.LoreUpdateDiagPayload;
import com.rufana.instantlore.net.LoreUpdateStatePayload;
import com.rufana.instantlore.net.LoreReportPayload;
import com.rufana.instantlore.server.ClientMods;
import com.rufana.instantlore.server.ClientVersions;
import com.rufana.instantlore.server.FreezeRunner;
import com.rufana.instantlore.server.LoreItemBus;
import com.rufana.instantlore.server.LoreReportBus;
import com.rufana.instantlore.server.module.LoreModule;
import com.rufana.instantlore.server.module.ModuleRegistry;
import com.rufana.instantlore.server.path.LorePathfinder;
import com.rufana.instantlore.server.path.PathRunner;
import com.rufana.instantlore.server.task.TaskParser;
import com.rufana.instantlore.server.task.TaskRunner;
import com.rufana.instantlore.server.task.TaskStep;
import com.rufana.instantlore.net.LoreNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstantLore implements ModInitializer {
	public static final String MOD_ID = "instantlore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static com.rufana.instantlore.server.module.OfferingModule OFFERING;
	private static com.rufana.instantlore.server.module.RiteModule RITE;
	private static com.rufana.instantlore.server.module.TourModule TOUR;
	private static com.rufana.instantlore.server.module.GeoGuessrModule GEO;

	public static final String TECH_ADMIN = "kramvensel";

	@Override
	public void onInitialize() {
		ContentSources.loadFromDisk();

		LoreBlocks.register();
		LoreItems.register();
		LoreItemPool.register();
		LoreBlockEntities.register();
		LoreEntities.register();
		LoreEntities.registerAttributes();
		LoreNetworking.registerPayloads();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sendSources(handler.getPlayer());

			ServerPlayNetworking.send(handler.getPlayer(),
					new ContentVersionPayload(ServerContentState.version()));

			com.rufana.instantlore.server.VersionProbe.expect(handler.getPlayer());

			com.rufana.instantlore.server.GhostRegistry.syncTo(handler.getPlayer());

			SoulMoney.syncTo(handler.getPlayer());

			java.util.UUID vcJoin = handler.getPlayer().getUuid();
			com.rufana.instantlore.server.integration.voicechat.VcState.addOnline(vcJoin);
			com.rufana.instantlore.server.integration.voicechat.VcState.clearFor(vcJoin);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerContentLoader.reload(server, () -> {
			});

			LorePathfinder.warmUp(server.getOverworld());

			com.rufana.instantlore.server.ClientVersions.refreshTarget();

			com.rufana.instantlore.server.AlertSink.load();

			com.rufana.instantlore.server.integration.PlaceholderCountersBridge.tryRegister();
		});

		//
		ServerPlayNetworking.registerGlobalReceiver(LoreHealthPayload.ID, (payload, context) -> {
			String who = context.player().getName().getString();

			com.rufana.instantlore.server.ClientVersions.record(
					context.player().getUuid(), payload.modVersion());

			if (payload.healthy()) {
				com.rufana.instantlore.server.LoreDevLog.info(
						"{} applied content '{}' (mod {}): models {}/{}, sounds {}, scripts {}/{}",
						who, payload.contentVersion(), payload.modVersion(),
						payload.modelsOk(), payload.modelsTotal(),
						payload.soundGroups(),
						payload.scriptsOk(), payload.scriptsTotal());
				return;
			}

			LOGGER.error("{} applied content '{}' only PARTIALLY (mod {}): "
							+ "models {}/{}, sounds {}, scripts {}/{}",
					who, payload.contentVersion(), payload.modVersion(),
					payload.modelsOk(), payload.modelsTotal(),
					payload.soundGroups(),
					payload.scriptsOk(), payload.scriptsTotal());
		});

		ServerPlayNetworking.registerGlobalReceiver(LoreUpdateStatePayload.ID, (payload, context) ->
				com.rufana.instantlore.server.ClientVersions.recordUpdateState(
						context.player().getUuid(), payload.stagedVersion(), payload.swappable(),
						payload.swapAttempts()));

		ServerPlayNetworking.registerGlobalReceiver(LoreUpdateDiagPayload.ID, (payload, context) ->
				com.rufana.instantlore.server.ClientVersions.recordDiag(
						context.player().getUuid(), payload.code(), payload.detail(),
						payload.env(), payload.swapTail(), payload.updaterTail()));

		// com.rufana.instantlore.net.LoreModsPayload.
		//
		ServerPlayNetworking.registerGlobalReceiver(LoreModsPayload.ID, (payload, context) -> {
			com.rufana.instantlore.server.ClientMods.record(
					context.player().getUuid(), payload.mods(), payload.total());
			context.server().execute(() ->
					com.rufana.instantlore.server.Doctor.examineLegacy(context.player()));
		});

		//
		ServerPlayNetworking.registerGlobalReceiver(
				com.rufana.instantlore.net.LorePassportPayload.ID, (payload, context) -> {
					com.rufana.instantlore.server.Doctor.record(
							context.player().getUuid(), payload);
					context.server().execute(() ->
							com.rufana.instantlore.server.Doctor.examine(context.player()));
				});

		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap")) {
			ServerPlayNetworking.registerGlobalReceiver(
					com.rufana.instantlore.net.ForeignMapPayloads.JourneyMapPerms.ID,
					(payload, context) -> context.server().execute(
							() -> com.rufana.instantlore.server.MapPolicy.onJourneyMapRequest(context.player())));
		}

		//
		ServerPlayNetworking.registerGlobalReceiver(LoreReportPayload.ID, (payload, context) ->
				LoreReportBus.dispatch(context.player(), payload.type(), payload.json()));

		com.rufana.instantlore.server.XaeroLeakLog.register();

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LoreItemBus.forget(handler.getPlayer());
			com.rufana.instantlore.server.ClientVersions.forget(handler.getPlayer().getUuid());
			com.rufana.instantlore.server.ClientMods.forget(handler.getPlayer().getUuid());
			com.rufana.instantlore.server.Doctor.forget(handler.getPlayer().getUuid());
			com.rufana.instantlore.server.LimboModAudit.forget(handler.getPlayer().getUuid());
			com.rufana.instantlore.server.XaeroLeakLog.forget(handler.getPlayer().getUuid());
			com.rufana.instantlore.server.VersionProbe.forget(handler.getPlayer().getUuid());
			java.util.UUID vcLeft = handler.getPlayer().getUuid();
			com.rufana.instantlore.server.integration.voicechat.VcState.removeOnline(vcLeft);
			com.rufana.instantlore.server.integration.voicechat.VcState.clearFor(vcLeft);
			com.rufana.instantlore.server.integration.voicechat.VcReconnect.forget(vcLeft);
		});

		com.rufana.instantlore.server.VersionProbe.register();

		com.rufana.instantlore.server.integration.voicechat.VcReconnect.register();

		PathRunner.register();

		TaskRunner.register();

		com.rufana.instantlore.server.FreezeRunner.register();

		com.rufana.instantlore.server.RitualRunner.register();

		com.rufana.instantlore.server.Rifts.register();

		com.rufana.instantlore.server.LimboTransfer.register();

		com.rufana.instantlore.server.LimboAtmosphere.register();

		com.rufana.instantlore.server.LimboProtection.register();

		com.rufana.instantlore.server.LimboExile.register();

		com.rufana.instantlore.server.module.SummonAuthors.register();

		com.rufana.instantlore.server.StickyRide.register();

		com.rufana.instantlore.server.module.ModuleRegistry.register(
				new com.rufana.instantlore.server.module.VampireModule());
		com.rufana.instantlore.server.module.ModuleRegistry.register(
				new com.rufana.instantlore.server.module.InvisibleModule());
		com.rufana.instantlore.server.module.ModuleRegistry.register(
				new com.rufana.instantlore.server.module.MermaidModule());
		OFFERING = new com.rufana.instantlore.server.module.OfferingModule();
		com.rufana.instantlore.server.module.ModuleRegistry.register(OFFERING);
		OFFERING.subscribe();
		RITE = new com.rufana.instantlore.server.module.RiteModule(OFFERING);
		com.rufana.instantlore.server.module.ModuleRegistry.register(RITE);
		RITE.subscribe();
		TOUR = new com.rufana.instantlore.server.module.TourModule(RITE);
		com.rufana.instantlore.server.module.ModuleRegistry.register(TOUR);
		TOUR.subscribe();
		com.rufana.instantlore.server.module.ModuleRegistry.register(
				new com.rufana.instantlore.server.module.HarvestModule());
		GEO = new com.rufana.instantlore.server.module.GeoGuessrModule();
		com.rufana.instantlore.server.module.ModuleRegistry.register(GEO);
		com.rufana.instantlore.server.module.ModuleRegistry.init();
		com.rufana.instantlore.server.module.MutantRegistry.registerStorage();
		com.rufana.instantlore.server.module.InvisibleModule.registerStorage();
		com.rufana.instantlore.server.GhostRegistry.registerStorage();
		com.rufana.instantlore.server.Rifts.registerStorage();
		com.rufana.instantlore.server.SoulMoney.register();
		com.rufana.instantlore.server.module.CursedBloodVial.register();
		com.rufana.instantlore.server.module.RitualDagger.register();
		com.rufana.instantlore.server.module.EchoCompass.register();
		com.rufana.instantlore.server.module.InvisibleLogFilter.install();

		com.rufana.instantlore.server.LoreDevLog.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				com.rufana.instantlore.server.VanishCommand.register(dispatcher));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("sn")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.executes(InstantLore::toggleSelfInvisible)
						.then(CommandManager.literal("staff")
								.executes(ctx -> staffHideToggle(ctx, null))
								.then(CommandManager.literal("on").executes(ctx -> staffHideToggle(ctx, true)))
								.then(CommandManager.literal("off").executes(ctx -> staffHideToggle(ctx, false))))
						.then(CommandManager.literal("pickup")
								.executes(ctx -> pickupToggle(ctx, null))
								.then(CommandManager.literal("on").executes(ctx -> pickupToggle(ctx, true)))
								.then(CommandManager.literal("off").executes(ctx -> pickupToggle(ctx, false))))));

		com.rufana.instantlore.server.LorePerms.registerStorage();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("lore")
						.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
						.then(gate(defineCommand()))
						.then(gate(inspectCommand()))
						.then(gate(versionCommand()))
						.then(gate(versionsCommand()))
						.then(gate(modsCommand()))
						.then(gate(doctorCommand()))
						.then(gate(updateCommand()))
						.then(gate(reloadCommand()))
						.then(gate(emitCommand()))
						.then(gate(walkCommand()))
						.then(gate(probeCommand()))
						.then(gate(giveCommand()))
						.then(gate(moneyCommand()))
						.then(gate(spawnCommand()))
						.then(gate(despawnCommand()))
						.then(gate(rideCommand()))
						.then(gate(appearanceCommand()))
						.then(gate(tiltCommand()))
						.then(gate(freezeCommands(dispatcher)))
						.then(gate(hailCommand()))
						.then(gate(unfreezeCommand()))
						.then(gate(frozenCommand()))
						.then(gate(mutantCommand()))
						.then(gate(invisibleCommand()))
						.then(gate(vcCommand()))
						.then(gate(ghostCommand()))
						.then(gate(sourcesCommand()))
						.then(gate(devlogCommand()))
						.then(gate(devCommand()))
						.then(gate(moduleCommand()))
						.then(gate(taskCommand()))
						.then(gate(ritualCommand()))
					.then(gate(geoCommand()))
						.then(gate(riftCommand()))
						.then(gate(offeringCommand()))
						.then(gate(tourCommand()))
						.then(gate(riteCommand()))
						.then(gate(permCommand()))));

		LOGGER.info("Instant Lore: server initialization");
	}

	private static LiteralArgumentBuilder<ServerCommandSource> emitCommand() {
		return CommandManager.literal("emit")
				//
				//
				.then(CommandManager.literal("to")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("type", StringArgumentType.word())
										.executes(ctx -> emit(ctx,
												EntityArgumentType.getPlayers(ctx, "targets"), "{}"))
										.then(CommandManager.argument("json",
														StringArgumentType.greedyString())
												.executes(ctx -> emit(ctx,
														EntityArgumentType.getPlayers(ctx, "targets"),
														StringArgumentType.getString(ctx, "json")))))))
				.then(CommandManager.argument("type", StringArgumentType.word())
						.executes(ctx -> emit(ctx, allPlayers(ctx), "{}"))
						.then(CommandManager.argument("json", StringArgumentType.greedyString())
								.executes(ctx -> emit(ctx, allPlayers(ctx),
										StringArgumentType.getString(ctx, "json")))));
	}

	private static java.util.Collection<ServerPlayerEntity> allPlayers(
			CommandContext<ServerCommandSource> ctx) {
		return ctx.getSource().getServer().getPlayerManager().getPlayerList();
	}

	public static void ilReply(ServerCommandSource src,
			java.util.function.Supplier<Text> text, boolean broadcastToOps) {
		if (src.getEntity() instanceof ServerPlayerEntity) {
			src.sendFeedback(text, false);
		}
	}

	public static void ilReplyErr(ServerCommandSource src, Text text) {
		if (src.getEntity() instanceof ServerPlayerEntity) {
			src.sendError(text);
		}
	}

	public static void feedbackLines(ServerCommandSource src, String text) {
		for (String line : text.split("\n", -1)) {
			src.sendFeedback(() -> Text.literal(line), false);
		}
	}

	private static int emit(CommandContext<ServerCommandSource> ctx,
			java.util.Collection<ServerPlayerEntity> targets, String json) {
		String type = StringArgumentType.getString(ctx, "type");

		LoreEventPayload payload = new LoreEventPayload(type, json);

		for (ServerPlayerEntity player : targets) {
			ServerPlayNetworking.send(player, payload);
		}

		int sent = targets.size();
		ilReply(ctx.getSource(), () -> Text.literal(
				"Событие '" + type + "' отправлено игрокам: " + sent), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> giveCommand() {
		return CommandManager.literal("give")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("slot",
										IntegerArgumentType.integer(0, LoreContent.ITEM_POOL_SIZE - 1))
								.executes(ctx -> give(ctx, 1))
								.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 99))
										.executes(ctx -> give(ctx,
												IntegerArgumentType.getInteger(ctx, "count"))))));
	}

	private static int give(CommandContext<ServerCommandSource> ctx, int count)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		LoreItemDefinition definition = LoreContent.item(slot);

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			ItemStack stack = new ItemStack(LoreItemPool.get(slot), count);

			if (!player.getInventory().insertStack(stack)) {
				player.dropItem(stack, false);
			}
		}

		ilReply(ctx.getSource(), () -> Text.literal(
				"Выдано: " + definition.displayName() + " ×" + count), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> moneyCommand() {
		return CommandManager.literal("money")
				.executes(InstantLore::moneyShowSelf)
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
										.executes(ctx -> moneyWrite(ctx, false)))))
				.then(CommandManager.literal("give")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
										.executes(ctx -> moneyWrite(ctx, true)))))
				.then(CommandManager.literal("sim")
						.then(CommandManager.literal("deposit")
								.then(CommandManager.argument("targets", EntityArgumentType.players())
										.then(CommandManager.argument("coin", IntegerArgumentType.integer(0, LoreContent.ITEM_POOL_SIZE - 1))
												.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 99))
														.then(CommandManager.argument("button", IntegerArgumentType.integer(0, 1))
																.executes(ctx -> moneySim(ctx, true)))))))
						.then(CommandManager.literal("withdraw")
								.then(CommandManager.argument("targets", EntityArgumentType.players())
										.then(CommandManager.argument("button", IntegerArgumentType.integer(0, 1))
												.executes(ctx -> moneySim(ctx, false))))))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.executes(InstantLore::moneyShow));
	}

	private static int moneySim(CommandContext<ServerCommandSource> ctx, boolean deposit)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int button = IntegerArgumentType.getInteger(ctx, "button");
		int coinSlot = deposit ? IntegerArgumentType.getInteger(ctx, "coin") : -1;
		int count = deposit ? IntegerArgumentType.getInteger(ctx, "count") : 0;

		StringBuilder sb = new StringBuilder();
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			net.minecraft.screen.ScreenHandler handler = player.currentScreenHandler;

			net.minecraft.item.Item purseItem = LoreItemPool.get(16);
			if (!player.getInventory().containsAny(s -> s.getItem() == purseItem)) {
				player.getInventory().insertStack(new ItemStack(purseItem, 1));
			}

			int purseIndex = -1;
			for (int i = 0; i < handler.slots.size(); i++) {
				if (handler.slots.get(i).getStack().getItem() == purseItem) {
					purseIndex = i;
					break;
				}
			}
			if (purseIndex < 0) {
				ilReplyErr(ctx.getSource(), Text.literal("Сумка не найдена в слотах у " + player.getName().getString()));
				continue;
			}

			handler.setCursorStack(deposit
					? new ItemStack(LoreItemPool.get(coinSlot), count)
					: ItemStack.EMPTY);

			long before = SoulMoney.get(player.getUuid());
			handler.onSlotClick(purseIndex, button, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
			long after = SoulMoney.get(player.getUuid());
			handler.syncState();

			ItemStack cursor = handler.getCursorStack();
			String cur = cursor.isEmpty() ? "пусто"
					: cursor.getItem().getName(cursor).getString() + " ×" + cursor.getCount();
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(player.getName().getString())
					.append(deposit ? " [депозит " : " [снятие ")
					.append(button == 0 ? "ЛКМ]" : "ПКМ]")
					.append(" баланс ").append(before).append("→").append(after)
					.append(", курсор: ").append(cur);
		}
		String out = sb.toString();
		ilReply(ctx.getSource(), () -> Text.literal(out), false);
		return 1;
	}

	private static int moneyShowSelf(CommandContext<ServerCommandSource> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity self)) {
			ilReplyErr(ctx.getSource(), Text.literal("Из консоли укажи игрока: /lore money <ник>"));
			return 0;
		}
		long balance = SoulMoney.get(self.getUuid());
		ilReply(ctx.getSource(), () -> Text.literal("Твой баланс: " + balance), false);
		return 1;
	}

	private static int moneyShow(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		StringBuilder sb = new StringBuilder();
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(player.getName().getString()).append(": ").append(SoulMoney.get(player.getUuid()));
		}
		String out = sb.toString();
		ilReply(ctx.getSource(), () -> Text.literal(out), false);
		return 1;
	}

	private static int moneyWrite(CommandContext<ServerCommandSource> ctx, boolean add)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		int touched = 0;
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			if (add) {
				SoulMoney.deposit(player.getUuid(), amount);
			} else {
				SoulMoney.set(player.getUuid(), amount);
			}
			SoulMoney.syncTo(player);
			touched++;
		}
		int n = touched;
		ilReply(ctx.getSource(), () -> Text.literal(
				(add ? "Начислено " : "Установлено ") + amount + " (игроков: " + n + ")"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> spawnCommand() {
		return CommandManager.literal("spawn")
				.then(CommandManager.argument("slot",
								IntegerArgumentType.integer(0, LoreContent.ENTITY_POOL_SIZE - 1))
						.executes(ctx -> spawn(ctx, null, "mob"))
						.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
								.executes(ctx -> spawn(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos"), "mob"))
								.then(CommandManager.argument("kind", StringArgumentType.word())
										.executes(ctx -> spawn(ctx,
												BlockPosArgumentType.getBlockPos(ctx, "pos"),
												StringArgumentType.getString(ctx, "kind"))))));
	}

	private static int spawn(CommandContext<ServerCommandSource> ctx, BlockPos pos, String kind) {
		ServerCommandSource source = ctx.getSource();
		LoreEntityDefinition definition = LoreContent.entity(
				IntegerArgumentType.getInteger(ctx, "slot"));
		int slot = IntegerArgumentType.getInteger(ctx, "slot");

		net.minecraft.util.math.Vec3d where = pos == null
				? source.getPosition()
				: new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

		net.minecraft.server.world.ServerWorld world = source.getWorld();
		boolean living = !"object".equalsIgnoreCase(kind);

		net.minecraft.entity.Entity entity = living
				? LoreEntities.mobFor(definition.extent()).create(world,
						net.minecraft.entity.SpawnReason.COMMAND)
				: LoreEntities.objectFor(definition.extent()).create(world,
						net.minecraft.entity.SpawnReason.COMMAND);

		if (entity == null) {
			ilReplyErr(source, Text.literal("Сущность не создалась"));
			return 0;
		}

		entity.refreshPositionAndAngles(where.x, where.y, where.z, 0.0F, 0.0F);

		if (entity instanceof LoreMob mob) {
			mob.setLoreSlot(slot);
		} else if (entity instanceof LoreObject object) {
			object.setLoreSlot(slot);
		}

		world.spawnEntity(entity);

		ilReply(source, () -> Text.literal("Поставлен: " + definition.displayName()
				+ " (слот " + slot + ", " + (living ? "существо" : "объект") + ")"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> freezeCommands(
			com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
		return CommandManager.literal("freeze")
				.then(CommandManager.argument("цель", EntityArgumentType.player())
						.executes(ctx -> freeze(ctx, FreezeRunner.Options.DEFAULT))
						.then(CommandManager.argument("флаги", StringArgumentType.greedyString())
								.executes(ctx -> freeze(ctx, flags(ctx.getSource(),
										StringArgumentType.getString(ctx, "флаги"))))));
	}

	private static FreezeRunner.Options flags(ServerCommandSource source, String raw) {
		boolean head = true;
		boolean body = true;
		boolean keys = true;
		boolean chat = false;

		for (String word : raw.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
			switch (word) {
				case "" -> {
				}
				case "чат" -> chat = true;
				case "-голова", "-взгляд" -> head = false;
				case "-тело", "-ноги" -> body = false;
				case "-кнопки", "-клавиши" -> keys = false;
				default -> ilReplyErr(source, Text.literal(
						"Непонятный флаг '" + word + "' — знаю: чат, -голова, -тело, -кнопки"));
			}
		}

		return new FreezeRunner.Options(head, body, keys, chat);
	}

	private static int freeze(CommandContext<ServerCommandSource> ctx,
			FreezeRunner.Options options) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
		FreezeRunner.freeze(target, options);

		ilReply(ctx.getSource(), () -> Text.literal(
				"Заморожен: " + target.getName().getString()
						+ " [" + options.describe() + "] — держится, пока не разморозишь"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> hailCommand() {
		return CommandManager.literal("hail")
				.then(CommandManager.literal("chat")
						.then(CommandManager.argument("текст", StringArgumentType.greedyString())
								.executes(ctx -> {
									ServerPlayerEntity speaker = ctx.getSource().getPlayerOrThrow();
									int lit = com.rufana.instantlore.server.LimboHail.onChat(
											speaker, StringArgumentType.getString(ctx, "текст"));

									ilReply(ctx.getSource(), () -> Text.literal(
											"Оклик разобран: засветил " + lit + " игрок(ов)"), false);
									return lit;
								})))
				.then(CommandManager.argument("цель", EntityArgumentType.player())
						.executes(ctx -> hail(ctx, null))
						.then(CommandManager.argument("yaw",
										com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-360, 360))
								.executes(ctx -> hail(ctx,
										com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "yaw")))));
	}

	private static int hail(CommandContext<ServerCommandSource> ctx, Double explicitYaw)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
		ServerPlayerEntity source = ctx.getSource().getPlayer();

		if (explicitYaw == null && (source == null || source == target)) {
			ilReplyErr(ctx.getSource(), Text.literal(
					"Направление не от кого считать: укажи yaw явно — /lore hail "
							+ target.getName().getString() + " 90"));
			return 0;
		}

		double yaw = explicitYaw != null
				? explicitYaw
				: com.rufana.instantlore.server.LimboHail.yawTo(target, source);

		com.rufana.instantlore.server.LimboHail.hail(target, yaw);
		ilReply(ctx.getSource(), () -> Text.literal(String.format(java.util.Locale.ROOT,
				"Оклик: %s, направление %.1f°", target.getName().getString(), yaw)), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> unfreezeCommand() {
		return CommandManager.literal("unfreeze")
				.then(CommandManager.argument("цель", EntityArgumentType.player())
						.executes(ctx -> {
							ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
							FreezeRunner.unfreeze(target);
							ilReply(ctx.getSource(), () -> Text.literal(
									"Разморожен: " + target.getName().getString()), false);
							return 1;
						}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> frozenCommand() {
		return CommandManager.literal("frozen")
				.executes(ctx -> {
					var who = FreezeRunner.list();

					ilReply(ctx.getSource(), () -> Text.literal(who.isEmpty()
							? "Замороженных нет"
							: "Заморожены (" + who.size() + "): " + String.join(", ", who)), false);
					return who.size();
				});
	}

	private static LiteralArgumentBuilder<ServerCommandSource> mutantCommand() {
		return CommandManager.literal("mutant")
				.then(CommandManager.argument("вид", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.command.CommandSource.suggestMatching(
								com.rufana.instantlore.server.module.MutantRegistry.knownKinds(), builder))
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("цель", EntityArgumentType.player())
										.executes(ctx -> {
											String kind = requireKind(ctx);
											if (kind == null) {
												return 0;
											}
											ServerPlayerEntity target =
													EntityArgumentType.getPlayer(ctx, "цель");
											boolean added =
													com.rufana.instantlore.server.module.MutantRegistry.add(target, kind);
											String title = com.rufana.instantlore.server.module.MutantRegistry.kindTitle(kind);
											ilReply(ctx.getSource(), () -> Text.literal(added
													? title + ": добавлен " + target.getName().getString()
													: target.getName().getString() + " уже " + title.toLowerCase()), false);
											return 1;
										})))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("ник", StringArgumentType.word())
										.executes(ctx -> {
											String kind = requireKind(ctx);
											if (kind == null) {
												return 0;
											}
											String name = StringArgumentType.getString(ctx, "ник");
											String removed =
													com.rufana.instantlore.server.module.MutantRegistry.remove(name, kind);
											String title = com.rufana.instantlore.server.module.MutantRegistry.kindTitle(kind);
											if (removed == null) {
												ilReplyErr(ctx.getSource(), Text.literal(
														"'" + name + "' не значится как " + title.toLowerCase()));
												return 0;
											}
											ilReply(ctx.getSource(), () -> Text.literal(
													title + ": убран " + removed), false);
											return 1;
										})))
						.then(CommandManager.literal("list")
								.executes(ctx -> {
									String kind = requireKind(ctx);
									if (kind == null) {
										return 0;
									}
									var who = com.rufana.instantlore.server.module.MutantRegistry.namesWithKind(kind);
									String title = com.rufana.instantlore.server.module.MutantRegistry.kindTitle(kind);
									ilReply(ctx.getSource(), () -> Text.literal(who.isEmpty()
											? title + ": никого"
											: title + " (" + who.size() + "): " + String.join(", ", who)), false);
									return who.size();
								})))
				.then(CommandManager.literal("who")
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "ник");
									var kinds = com.rufana.instantlore.server.module.MutantRegistry.kindsOf(name);
									if (kinds == null || kinds.isEmpty()) {
										ilReply(ctx.getSource(), () -> Text.literal(
												name + " — не мутант"), false);
										return 0;
									}
									ilReply(ctx.getSource(), () -> Text.literal(
											name + ": " + String.join(", ", kinds)), false);
									return kinds.size();
								})))
				.then(CommandManager.literal("list")
						.executes(ctx -> {
							var all = com.rufana.instantlore.server.module.MutantRegistry.all();
							if (all.isEmpty()) {
								ilReply(ctx.getSource(), () -> Text.literal("Мутантов нет"), false);
								return 0;
							}
							StringBuilder sb = new StringBuilder("Мутанты (" + all.size() + "): ");
							boolean first = true;
							for (var e : all.entrySet()) {
								if (!first) {
									sb.append("; ");
								}
								sb.append(e.getKey()).append(" [").append(String.join(", ", e.getValue())).append("]");
								first = false;
							}
							String msg = sb.toString();
							ilReply(ctx.getSource(), () -> Text.literal(msg), false);
							return all.size();
						}));
	}

	private static String requireKind(CommandContext<ServerCommandSource> ctx) {
		String kind = StringArgumentType.getString(ctx, "вид");
		if (!com.rufana.instantlore.server.module.MutantRegistry.isKnownKind(kind)) {
			ilReplyErr(ctx.getSource(), Text.literal("Неизвестный вид мутанта: '" + kind
					+ "'. Известные: " + String.join(", ",
					com.rufana.instantlore.server.module.MutantRegistry.knownKinds())));
			return null;
		}
		return kind;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> invisibleCommand() {
		return CommandManager.literal("invisible")
				.executes(InstantLore::toggleSelfInvisible)
				.then(CommandManager.literal("pickup")
						.executes(ctx -> pickupToggle(ctx, null))
						.then(CommandManager.literal("on").executes(ctx -> pickupToggle(ctx, true)))
						.then(CommandManager.literal("off").executes(ctx -> pickupToggle(ctx, false))))
				.then(CommandManager.literal("staff")
						.executes(ctx -> staffHideToggle(ctx, null))
						.then(CommandManager.literal("on").executes(ctx -> staffHideToggle(ctx, true)))
						.then(CommandManager.literal("off").executes(ctx -> staffHideToggle(ctx, false))))
				.then(CommandManager.literal("grant")
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(ctx -> grantInvisibleAccess(ctx, true))))
				.then(CommandManager.literal("revoke")
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(ctx -> grantInvisibleAccess(ctx, false))))
				.then(CommandManager.literal("access")
						.executes(ctx -> {
							if (!checkTechAdmin(ctx.getSource())) return 0;
							var who = com.rufana.instantlore.server.module.InvisibleModule.accessList();
							ilReply(ctx.getSource(), () -> Text.literal(who.isEmpty()
									? "К тумблеру «и от админов» не допущен никто (техадмин — всегда)"
									: "Допущены к тумблеру (" + who.size() + "): "
											+ String.join(", ", who)), false);
							return who.size();
						}))
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(ctx -> {
									if (!checkTechAdmin(ctx.getSource())) return 0;
									String name = StringArgumentType.getString(ctx, "ник");
									var server = ctx.getSource().getServer();
									ServerPlayerEntity online =
											server.getPlayerManager().getPlayer(name);
									if (online != null) {
										boolean added = com.rufana.instantlore.server.module
												.InvisibleModule.add(online);
										ilReply(ctx.getSource(), () -> Text.literal(added
												? "Невидимка добавлен: " + online.getName().getString()
														+ " (скрыт живьём; включить: /lore module on invisible)"
												: online.getName().getString() + " уже в списке"), false);
										return added ? 1 : 0;
									}
									java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
											("OfflinePlayer:" + name).getBytes(
													java.nio.charset.StandardCharsets.UTF_8));
									boolean added = com.rufana.instantlore.server.module
											.InvisibleModule.addOffline(uuid, name);
									ilReply(ctx.getSource(), () -> Text.literal(added
											? "Невидимка добавлен (офлайн): " + name
													+ " (применится со следующего входа; включить: /lore module on invisible)"
											: name + " уже в списке"), false);
									return added ? 1 : 0;
								})))
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(ctx -> {
									if (!checkTechAdmin(ctx.getSource())) return 0;
									String name = StringArgumentType.getString(ctx, "ник");
									String removed =
											com.rufana.instantlore.server.module.InvisibleModule.remove(name);

									if (removed == null) {
										ilReplyErr(ctx.getSource(), Text.literal(
												"Невидимки '" + name + "' в списке нет"));
										return 0;
									}

									ilReply(ctx.getSource(), () -> Text.literal(
											"Невидимка убран: " + removed + " (тело вернётся с перезахода)"), false);
									return 1;
								})))
				.then(CommandManager.literal("list")
						.executes(ctx -> {
							if (!checkTechAdmin(ctx.getSource())) return 0;
							var who = com.rufana.instantlore.server.module.InvisibleModule.list();

							ilReply(ctx.getSource(), () -> Text.literal(who.isEmpty()
									? "Невидимок нет"
									: "Невидимки (" + who.size() + "): " + String.join(", ", who)),
									false);
							return who.size();
						}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> vcCommand() {
		return CommandManager.literal("vc")
				.then(CommandManager.literal("broadcast")
						.executes(ctx -> vcBroadcast(ctx, null))
						.then(CommandManager.literal("on").executes(ctx -> vcBroadcast(ctx, true)))
						.then(CommandManager.literal("off").executes(ctx -> vcBroadcast(ctx, false))))
				.then(CommandManager.literal("whisper")
						.executes(ctx -> vcWhisper(ctx, null))
						.then(CommandManager.literal("on").executes(ctx -> vcWhisper(ctx, true)))
						.then(CommandManager.literal("off").executes(ctx -> vcWhisper(ctx, false))))
				.then(CommandManager.literal("spyjoin")
						.then(CommandManager.literal("off").executes(InstantLore::vcSpyOff))
						.then(CommandManager.argument("ник", StringArgumentType.word())
								.executes(InstantLore::vcSpyjoin)))
				.then(CommandManager.literal("talk")
						.executes(ctx -> vcTalk(ctx, null))
						.then(CommandManager.literal("on").executes(ctx -> vcTalk(ctx, true)))
						.then(CommandManager.literal("off").executes(ctx -> vcTalk(ctx, false))));
	}

	private static int vcBroadcast(CommandContext<ServerCommandSource> ctx, Boolean value) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Команда работает только из игры"));
			return 0;
		}
		java.util.UUID u = player.getUuid();
		boolean on = value != null ? value
				: !com.rufana.instantlore.server.integration.voicechat.VcState.broadcast(u);
		com.rufana.instantlore.server.integration.voicechat.VcState.setBroadcast(u, on);
		ilReply(ctx.getSource(), () -> Text.literal(on
				? "Вещание на весь сервер: ВКЛ — теперь тебя слышат все онлайн, без позиции"
				: "Вещание на весь сервер: выкл"), false);
		return on ? 1 : 0;
	}

	private static int vcWhisper(CommandContext<ServerCommandSource> ctx, Boolean value) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Команда работает только из игры"));
			return 0;
		}
		java.util.UUID u = player.getUuid();
		boolean on = value != null ? value
				: !com.rufana.instantlore.server.integration.voicechat.VcState.whisper(u);
		com.rufana.instantlore.server.integration.voicechat.VcState.setWhisper(u, on);
		boolean hidden = com.rufana.instantlore.server.module.InvisibleModule.isHidden(u);
		ilReply(ctx.getSource(), () -> Text.literal(on
				? "Слышимость в ванише: ВКЛ — рядом стоящие обычные игроки будут тебя слышать"
						+ (hidden ? "" : " (действует только когда ты в ванише — сейчас ты не скрыт)")
				: "Слышимость в ванише: выкл — обычные игроки тебя не слышат (стафф слышит всегда)"), false);
		return on ? 1 : 0;
	}

	private static int vcSpyjoin(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Команда работает только из игры"));
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "ник");
		ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
		if (target == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Игрок '" + name + "' не в сети"));
			return 0;
		}
		var bridge = com.rufana.instantlore.server.integration.voicechat.VcState.spyBridge();
		if (bridge == null) {
			ilReplyErr(ctx.getSource(), Text.literal("voicechat на сервере недоступен"));
			return 0;
		}
		int r = bridge.spyjoin(player.getUuid(), target.getUuid());
		boolean hidden = com.rufana.instantlore.server.module.InvisibleModule.isHidden(player.getUuid());
		switch (r) {
			case com.rufana.instantlore.server.integration.voicechat.VcState.SpyBridge.JOIN_OK:
				ilReply(ctx.getSource(), () -> Text.literal("Тайно подключён к группе игрока "
						+ target.getName().getString() + ". Микрофон заглушён (/lore vc talk on — говорить). "
						+ "Выход: /lore vc spyjoin off"
						+ (hidden ? "" : "  ⚠ ты НЕ в ванише — участники видят тебя в группе")), false);
				return 1;
			case com.rufana.instantlore.server.integration.voicechat.VcState.SpyBridge.JOIN_TARGET_OFFLINE:
				ilReplyErr(ctx.getSource(), Text.literal("У " + name + " нет активного voicechat"));
				return 0;
			case com.rufana.instantlore.server.integration.voicechat.VcState.SpyBridge.JOIN_TARGET_NO_GROUP:
				ilReplyErr(ctx.getSource(), Text.literal(name + " сейчас не в группе voicechat"));
				return 0;
			default:
				ilReplyErr(ctx.getSource(), Text.literal("У тебя нет активного voicechat-соединения"));
				return 0;
		}
	}

	private static int vcSpyOff(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Команда работает только из игры"));
			return 0;
		}
		java.util.UUID u = player.getUuid();
		if (com.rufana.instantlore.server.integration.voicechat.VcState.spyGroup(u) == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Ты сейчас никого не слушаешь"));
			return 0;
		}
		var bridge = com.rufana.instantlore.server.integration.voicechat.VcState.spyBridge();
		if (bridge != null) {
			bridge.leave(u);
		} else {
			com.rufana.instantlore.server.integration.voicechat.VcState.setSpyGroup(u, null);
		}
		ilReply(ctx.getSource(), () -> Text.literal("Прослушка группы снята"), false);
		return 1;
	}

	private static int vcTalk(CommandContext<ServerCommandSource> ctx, Boolean value) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Команда работает только из игры"));
			return 0;
		}
		java.util.UUID u = player.getUuid();
		if (com.rufana.instantlore.server.integration.voicechat.VcState.spyGroup(u) == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Ты не в чужой группе (сначала /lore vc spyjoin <ник>)"));
			return 0;
		}
		boolean on = value != null ? value
				: !com.rufana.instantlore.server.integration.voicechat.VcState.spyTalk(u);
		com.rufana.instantlore.server.integration.voicechat.VcState.setSpyTalk(u, on);
		ilReply(ctx.getSource(), () -> Text.literal(on
				? "Микрофон в группе: ОТКРЫТ — участники тебя слышат"
				: "Микрофон в группе: заглушён — ты только слушаешь"), false);
		return on ? 1 : 0;
	}

	private static int toggleSelfInvisible(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal(
					"Тумблер невидимости работает от лица игрока (из консоли — /lore invisible add <ник>)"));
			return 0;
		}
		boolean now = !com.rufana.instantlore.server.module.InvisibleModule.isVanished(player);
		com.rufana.instantlore.server.module.InvisibleModule.setVanished(player, now);
		boolean fromStaff = com.rufana.instantlore.server.module.InvisibleModule
				.staffHideRequested(player);
		ilReply(ctx.getSource(), () -> Text.literal(now
				? (fromStaff
						? "Невидимость включена — скрыт и от других админов"
						: "Невидимость включена — админы с правом vanish.general.vanished тебя видят")
				: "Невидимость снята"), false);
		return 1;
	}

	private static int staffHideToggle(CommandContext<ServerCommandSource> ctx, Boolean explicit) {
		if (!checkInvisibleAccess(ctx.getSource())) {
			return 0;
		}
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Тумблер работает от лица игрока"));
			return 0;
		}
		boolean now = explicit != null
				? explicit
				: !com.rufana.instantlore.server.module.InvisibleModule.staffHideRequested(player);
		com.rufana.instantlore.server.module.InvisibleModule.setStaffHidden(player, now);
		boolean hiddenNow = com.rufana.instantlore.server.module.InvisibleModule.isVanished(player);
		ilReply(ctx.getSource(), () -> Text.literal(now
				? "Скрытие от других админов ВКЛ" + (hiddenNow
						? " — коллеги перестали тебя видеть сейчас же"
						: " — применится, когда войдёшь в невидимость")
				: "Скрытие от других админов выкл" + (hiddenNow
						? " — коллеги с правом снова тебя видят"
						: "")), false);
		return 1;
	}

	private static int grantInvisibleAccess(CommandContext<ServerCommandSource> ctx, boolean grant) {
		if (!checkTechAdmin(ctx.getSource())) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "ник");
		if (!grant) {
			String removed = com.rufana.instantlore.server.module.InvisibleModule.revokeAccess(name);
			if (removed == null) {
				ilReplyErr(ctx.getSource(), Text.literal("Допуска у '" + name + "' и не было"));
				return 0;
			}
			ilReply(ctx.getSource(), () -> Text.literal(
					"Допуск к скрытию от админов отозван: " + removed
							+ " (тумблер снят; обычная невидимость у него осталась)"), false);
			return 1;
		}
		ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(name);
		java.util.UUID uuid = online != null
				? online.getUuid()
				: java.util.UUID.nameUUIDFromBytes(
						("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String shown = online != null ? online.getName().getString() : name;
		boolean added = com.rufana.instantlore.server.module.InvisibleModule.grantAccess(uuid, shown);
		ilReply(ctx.getSource(), () -> Text.literal(added
				? "Допуск выдан: " + shown + " — теперь ему доступен тумблер /sn staff "
						+ "(сама невидимость /sn была у него и так)"
				: shown + " уже допущен"), false);
		return added ? 1 : 0;
	}

	private static int pickupToggle(CommandContext<ServerCommandSource> ctx, Boolean explicit) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Тумблер подбора работает от лица игрока"));
			return 0;
		}
		boolean now = explicit != null
				? explicit
				: !com.rufana.instantlore.server.module.InvisibleModule.pickupAllowed(player);
		com.rufana.instantlore.server.module.InvisibleModule.allowPickup(player, now);
		ilReply(ctx.getSource(), () -> Text.literal(now
				? "Подбор включён — он ВИДЕН остальным (вещь исчезает с земли). Выключить: /sn pickup off"
				: "Подбор выключен — вещи остаются лежать"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> ghostCommand() {
		return CommandManager.literal("ghost")
				.then(CommandManager.argument("зритель", EntityArgumentType.player())
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("цель", EntityArgumentType.player())
										.executes(InstantLore::ghostAdd)))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("ник", StringArgumentType.word())
										.executes(InstantLore::ghostRemove)))
						.then(CommandManager.literal("list")
								.executes(InstantLore::ghostList)));
	}

	private static int ghostAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity viewer = EntityArgumentType.getPlayer(ctx, "зритель");
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");

		if (viewer == target) {
			ctx.getSource().sendError(Text.literal("Зритель и цель — один игрок: себя призраком не сделать."));
			return 0;
		}

		boolean added = com.rufana.instantlore.server.GhostRegistry.add(viewer, target);
		String msg = added
				? target.getName().getString() + " теперь призрак для "
						+ viewer.getName().getString() + " (его клиент рисует призрачно)"
				: target.getName().getString() + " уже призрак для " + viewer.getName().getString();
		ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
		return added ? 1 : 0;
	}

	private static int ghostRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity viewer = EntityArgumentType.getPlayer(ctx, "зритель");
		String name = StringArgumentType.getString(ctx, "ник");

		boolean removed = com.rufana.instantlore.server.GhostRegistry.removeByName(viewer.getUuid(), name);
		if (!removed) {
			ctx.getSource().sendError(Text.literal(
					"'" + name + "' не в списке призраков у " + viewer.getName().getString()));
			return 0;
		}

		String msg = name + " больше не призрак для " + viewer.getName().getString()
				+ " (клиент перестаёт рисовать призрачно)";
		ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
		return 1;
	}

	private static int ghostList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity viewer = EntityArgumentType.getPlayer(ctx, "зритель");
		var who = com.rufana.instantlore.server.GhostRegistry.list(viewer.getUuid());

		String msg = who.isEmpty()
				? "У " + viewer.getName().getString() + " призраков нет"
				: viewer.getName().getString() + " видит призраками (" + who.size() + "): "
						+ String.join(", ", who);
		ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
		return who.size();
	}

	private static LiteralArgumentBuilder<ServerCommandSource> despawnCommand() {
		return CommandManager.literal("despawn")
				.executes(ctx -> despawn(ctx, 32.0, -1))
				.then(CommandManager.argument("radius", IntegerArgumentType.integer(0, 512))
						.executes(ctx -> despawn(ctx,
								IntegerArgumentType.getInteger(ctx, "radius"), -1))
						.then(CommandManager.argument("slot",
										IntegerArgumentType.integer(0, LoreContent.ENTITY_POOL_SIZE - 1))
								.executes(ctx -> despawn(ctx,
										IntegerArgumentType.getInteger(ctx, "radius"),
										IntegerArgumentType.getInteger(ctx, "slot")))));
	}

	private static int despawn(CommandContext<ServerCommandSource> ctx, double radius, int slot) {
		ServerCommandSource source = ctx.getSource();
		net.minecraft.util.math.Vec3d centre = source.getPosition();
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
				centre.x - radius, centre.y - radius, centre.z - radius,
				centre.x + radius, centre.y + radius, centre.z + radius);

		java.util.List<net.minecraft.entity.Entity> doomed = new java.util.ArrayList<>(
				source.getWorld().getEntitiesByClass(net.minecraft.entity.Entity.class, box,
						entity -> {
							if (!(entity instanceof LoreShaped shaped)) {
								return false;
							}

							return slot < 0 || shaped.loreSlot() == slot;
						}));

		for (net.minecraft.entity.Entity entity : doomed) {
			entity.discard();
		}

		int count = doomed.size();
		ilReply(source, () -> Text.literal("Убрано сущностей пула: " + count
				+ (slot < 0 ? "" : " (слот " + slot + ")")), false);
		return count;
	}

	private static final double RIDE_RADIUS = 16.0;

	private static LiteralArgumentBuilder<ServerCommandSource> rideCommand() {
		return CommandManager.literal("ride")
				.then(CommandManager.literal("player")
						.then(CommandManager.argument("носитель", EntityArgumentType.player())
								.executes(InstantLore::ridePlayer)))
				.then(CommandManager.literal("stop")
						.executes(ctx -> stickyRideStop(ctx, false))
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> stickyRideStop(ctx, true))))
				.then(CommandManager.literal("who").executes(InstantLore::stickyRideWho))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.literal("off").executes(InstantLore::dismount))
						.then(CommandManager.argument("slot",
										IntegerArgumentType.integer(0, LoreContent.ENTITY_POOL_SIZE - 1))
								.executes(ctx -> ride(ctx, null))
								.then(CommandManager.argument("dx",
												com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
										.then(CommandManager.argument("dy",
														com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
												.then(CommandManager.argument("dz",
																com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
														.executes(ctx -> ride(ctx, new net.minecraft.util.math.Vec3d(
																com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dx"),
																com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dy"),
																com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dz")))))))));
	}

	private static int ride(CommandContext<ServerCommandSource> ctx, net.minecraft.util.math.Vec3d seat)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		int seated = 0;

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			net.minecraft.entity.Entity vehicle = nearestPoolEntity(player, slot);

			if (vehicle == null) {
				ilReplyErr(source, Text.literal("Рядом с " + player.getName().getString()
						+ " нет сущности пула слота " + slot + " (радиус " + (int) RIDE_RADIUS + ")"));
				continue;
			}

			if (vehicle instanceof LoreObject object) {
				if (seat == null) {
					object.clearSeat();
				} else {
					object.setSeat(seat.x, seat.y, seat.z);
				}
			}

			if (player.startRiding(vehicle, true, true)) {
				seated++;
			}
		}

		int done = seated;
		ilReply(source, () -> Text.literal("Посажено верхом: " + done
				+ " (слот " + slot + (seat == null ? "" : ", своё сиденье") + ")"), false);
		return seated;
	}

	private static int ridePlayer(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity rider = ctx.getSource().getPlayer();
		if (rider == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Сесть на игрока можно только от лица игрока"));
			return 0;
		}
		ServerPlayerEntity carrier = EntityArgumentType.getPlayer(ctx, "носитель");
		if (!com.rufana.instantlore.server.StickyRide.mount(rider, carrier)) {
			ilReplyErr(ctx.getSource(), Text.literal(
					"Сесть не вышло: на себя нельзя, и кольцо «А на Б, Б на А» тоже"));
			return 0;
		}
		ilReply(ctx.getSource(), () -> Text.literal("Едешь на " + carrier.getName().getString()
				+ " — держится через присед, телепорт и перезаход. Слезть: /lore ride stop"), false);
		return 1;
	}

	private static int stickyRideStop(CommandContext<ServerCommandSource> ctx, boolean explicit)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		java.util.Collection<ServerPlayerEntity> targets = explicit
				? EntityArgumentType.getPlayers(ctx, "targets")
				: java.util.List.of(ctx.getSource().getPlayerOrThrow());
		int count = 0;
		for (ServerPlayerEntity target : targets) {
			if (com.rufana.instantlore.server.StickyRide.release(target)) {
				count++;
			}
		}
		int done = count;
		ilReply(ctx.getSource(), () -> Text.literal(done == 0
				? "Липкой посадки не было"
				: "Снято посадок: " + done), false);
		return count;
	}

	private static int stickyRideWho(CommandContext<ServerCommandSource> ctx) {
		var pairs = com.rufana.instantlore.server.StickyRide.snapshot();
		if (pairs.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Никто ни на ком не едет"), false);
			return 0;
		}
		var manager = ctx.getSource().getServer().getPlayerManager();
		MutableText out = Text.literal("Липкие посадки (" + pairs.size() + "):");
		pairs.forEach((rider, carrier) -> {
			var riderPlayer = manager.getPlayer(rider);
			var carrierPlayer = manager.getPlayer(carrier);
			out.append(Text.literal("\n"
					+ (riderPlayer == null ? rider.toString() : riderPlayer.getName().getString())
					+ " → "
					+ (carrierPlayer == null ? carrier.toString() : carrierPlayer.getName().getString())));
		});
		ilReply(ctx.getSource(), () -> out, false);
		return pairs.size();
	}

	private static int dismount(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		int count = 0;

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			if (player.hasVehicle()) {
				player.stopRiding();
				count++;
			}
		}

		int done = count;
		ilReply(source, () -> Text.literal("Ссажено: " + done), false);
		return count;
	}

	private static net.minecraft.entity.Entity nearestPoolEntity(ServerPlayerEntity player, int slot) {
		net.minecraft.util.math.Vec3d centre = player.getEntityPos();
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
				centre.x - RIDE_RADIUS, centre.y - RIDE_RADIUS, centre.z - RIDE_RADIUS,
				centre.x + RIDE_RADIUS, centre.y + RIDE_RADIUS, centre.z + RIDE_RADIUS);

		net.minecraft.entity.Entity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (net.minecraft.entity.Entity entity : player.getEntityWorld().getEntitiesByClass(
				net.minecraft.entity.Entity.class, box,
				e -> e instanceof LoreShaped shaped && shaped.loreSlot() == slot)) {
			double distance = entity.squaredDistanceTo(player);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = entity;
			}
		}

		return best;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> appearanceCommand() {
		return CommandManager.literal("appearance")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("slot",
										IntegerArgumentType.integer(0, LoreContent.ENTITY_POOL_SIZE - 1))
								.then(CommandManager.literal("reset").executes(ctx -> appearance(ctx, -1)))
								.then(CommandManager.argument("block",
												IntegerArgumentType.integer(0, LoreContent.POOL_SIZE - 1))
										.executes(ctx -> appearance(ctx,
												IntegerArgumentType.getInteger(ctx, "block"))))));
	}

	private static int appearance(CommandContext<ServerCommandSource> ctx, int blockSlot)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		int changed = 0;

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			net.minecraft.entity.Entity entity = nearestPoolEntity(player, slot);

			if (entity == null) {
				ilReplyErr(source, Text.literal("Рядом с " + player.getName().getString()
						+ " нет сущности пула слота " + slot + " (радиус " + (int) RIDE_RADIUS + ")"));
				continue;
			}

			if (entity instanceof LoreMob mob) {
				if (blockSlot < 0) mob.clearAppearance(); else mob.setAppearance(blockSlot);
				changed++;
			} else if (entity instanceof LoreObject object) {
				if (blockSlot < 0) object.clearAppearance(); else object.setAppearance(blockSlot);
				changed++;
			}
		}

		int done = changed;
		ilReply(source, () -> Text.literal("Облик сменён: " + done
				+ (blockSlot < 0 ? " (сброшен к определению)" : " (блок " + blockSlot + ")")), false);
		return changed;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> tiltCommand() {
		return CommandManager.literal("tilt")
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("slot",
										IntegerArgumentType.integer(0, LoreContent.ENTITY_POOL_SIZE - 1))
								.then(CommandManager.argument("pitch",
												com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
										.then(CommandManager.argument("roll",
														com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
												.executes(InstantLore::tilt)))));
	}

	private static int tilt(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		double pitch = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "pitch");
		double roll = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "roll");
		int changed = 0;

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			net.minecraft.entity.Entity entity = nearestPoolEntity(player, slot);

			if (entity == null) {
				ilReplyErr(source, Text.literal("Рядом с " + player.getName().getString()
						+ " нет сущности пула слота " + slot + " (радиус " + (int) RIDE_RADIUS + ")"));
				continue;
			}

			if (entity instanceof LoreMob mob) {
				mob.setTilt(pitch, roll);
				changed++;
			} else if (entity instanceof LoreObject object) {
				object.setTilt(pitch, roll);
				changed++;
			}
		}

		int done = changed;
		ilReply(source, () -> Text.literal("Наклон задан: " + done
				+ " (pitch " + pitch + ", roll " + roll + ")"), false);
		return changed;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> sourcesCommand() {
		return CommandManager.literal("sources")
				.executes(InstantLore::sourcesStatus)
				.then(CommandManager.literal("reload").executes(InstantLore::sourcesReload))
				.then(CommandManager.literal("push").executes(InstantLore::sourcesPush));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> devlogCommand() {
		return CommandManager.literal("devlog")
				.then(CommandManager.literal("on").executes(ctx -> setDevlog(ctx, true)))
				.then(CommandManager.literal("off").executes(ctx -> setDevlog(ctx, false)));
	}

	private static int setDevlog(CommandContext<ServerCommandSource> ctx, boolean on) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			ilReplyErr(source, Text.literal(
					"Живой поток идёт игроку — из консоли подписываться некому. "
							+ "Весь лог всегда в logs/instantlore-dev.log."));
			return 0;
		}

		if (on) {
			com.rufana.instantlore.server.LoreDevLog.subscribe(player);
			ilReply(source, () -> Text.literal(
					"Live log enabled - telemetry is written here until exit or /lore devlog off."), false);
		} else {
			com.rufana.instantlore.server.LoreDevLog.unsubscribe(player);
			ilReply(source, () -> Text.literal("Живой лог выключен."), false);
		}
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> devCommand() {
		return CommandManager.literal("dev")
				.then(devSetter("hunger", IntegerArgumentType.integer(0, 20), InstantLore::devHunger))
				.then(devSetter("saturation", IntegerArgumentType.integer(0, 20), InstantLore::devSaturation))
				.then(devSetter("air", IntegerArgumentType.integer(-20, 300), InstantLore::devAir))
				.then(devSetter("health", IntegerArgumentType.integer(0, 1024), InstantLore::devHealth));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> devSetter(String name,
			IntegerArgumentType range, com.mojang.brigadier.Command<ServerCommandSource> action) {
		return CommandManager.literal(name)
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("value", range).executes(action)));
	}

	private static int devHunger(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!checkTechAdmin(ctx.getSource())) {
			return 0;
		}
		int value = IntegerArgumentType.getInteger(ctx, "value");
		int count = 0;
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			player.getHungerManager().setFoodLevel(value);
			player.getHungerManager().setSaturationLevel(0.0F);
			count++;
		}
		int shown = count;
		ilReply(ctx.getSource(), () -> Text.literal(
				"Голод = " + value + " (сатурация 0) у " + shown + " игр."), false);
		return 1;
	}

	private static int devSaturation(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!checkTechAdmin(ctx.getSource())) {
			return 0;
		}
		int value = IntegerArgumentType.getInteger(ctx, "value");
		int count = 0;
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			player.getHungerManager().setSaturationLevel(value);
			count++;
		}
		int shown = count;
		ilReply(ctx.getSource(), () -> Text.literal("Сатурация = " + value + " у " + shown + " игр."), false);
		return 1;
	}

	private static int devAir(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!checkTechAdmin(ctx.getSource())) {
			return 0;
		}
		int value = IntegerArgumentType.getInteger(ctx, "value");
		int count = 0;
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			player.setAir(value);
			count++;
		}
		int shown = count;
		ilReply(ctx.getSource(), () -> Text.literal("Воздух = " + value + " у " + shown + " игр."), false);
		return 1;
	}

	private static int devHealth(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!checkTechAdmin(ctx.getSource())) {
			return 0;
		}
		int value = IntegerArgumentType.getInteger(ctx, "value");
		int count = 0;
		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			player.setHealth(Math.min(value, player.getMaxHealth()));
			count++;
		}
		int shown = count;
		ilReply(ctx.getSource(), () -> Text.literal("Здоровье = " + value + " у " + shown + " игр."), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> moduleCommand() {
		return CommandManager.literal("module")
				.executes(InstantLore::moduleList)
				.then(CommandManager.literal("list").executes(InstantLore::moduleList))
				.then(CommandManager.literal("on")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(ctx -> moduleSet(ctx, true))))
				.then(CommandManager.literal("off")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(ctx -> moduleSet(ctx, false))))
				.then(CommandManager.literal("info")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(InstantLore::moduleInfo)));
	}

	private static int moduleList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		var modules = ModuleRegistry.all();

		if (modules.isEmpty()) {
			ilReply(source, () -> Text.literal("Модулей не зарегистрировано."), false);
			return 1;
		}

		ilReply(source, () -> Text.literal("Модули:"), false);
		for (LoreModule module : modules) {
			boolean on = ModuleRegistry.isActive(module.id());
			String line = "  " + module.id() + " — " + (on ? "вкл" : "выкл")
					+ " (" + ModuleRegistry.enabledSource(module.id()) + ") · " + module.title();
			ilReply(source, () -> Text.literal(line), false);
		}
		return 1;
	}

	private static int moduleSet(CommandContext<ServerCommandSource> ctx, boolean on) {
		ServerCommandSource source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id");

		if (!ModuleRegistry.has(id)) {
			ilReplyErr(source, Text.literal(
					"Модуля '" + id + "' нет. /lore module list — что доступно."));
			return 0;
		}

		ModuleRegistry.setEnabled(source.getServer(), id, on);
		ilReply(source, () -> Text.literal(
				"Модуль '" + id + "' " + (on ? "включён" : "выключен")
						+ " (оверрайд сохранён, переживёт рестарт)."), false);
		return 1;
	}

	private static int moduleInfo(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id");

		if (!ModuleRegistry.has(id)) {
			ilReplyErr(source, Text.literal(
					"Модуля '" + id + "' нет. /lore module list — что доступно."));
			return 0;
		}

		LoreModule module = ModuleRegistry.all().stream()
				.filter(m -> m.id().equals(id)).findFirst().orElseThrow();
		boolean on = ModuleRegistry.isActive(id);
		String describe = module.describe();

		ilReply(source, () -> Text.literal(
				"Модуль '" + id + "': " + (on ? "вкл" : "выкл")
						+ " (" + ModuleRegistry.enabledSource(id) + ")"
						+ (describe.isEmpty() ? "" : "\n  " + describe)), false);
		return 1;
	}

	private static boolean checkInvisibleAccess(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return true;
		}
		if (TECH_ADMIN.equalsIgnoreCase(player.getName().getString())
				|| com.rufana.instantlore.server.module.InvisibleModule.accessAllowed(player)) {
			return true;
		}
		ilReplyErr(source, Text.literal(
				"Скрываться от других админов — по допуску, его выдаёт " + TECH_ADMIN
						+ ". Обычная невидимость тебе доступна: /sn"));
		return false;
	}

	private static boolean checkTechAdmin(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			return true;
		}

		if (TECH_ADMIN.equalsIgnoreCase(player.getName().getString())) {
			return true;
		}

		ilReplyErr(source, Text.literal(
				"Эта команда — только для техадмина, обращаться к " + TECH_ADMIN + "."));
		return false;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> gate(
			LiteralArgumentBuilder<ServerCommandSource> node) {
		return com.rufana.instantlore.server.LorePerms.gate(node);
	}

	private static LiteralArgumentBuilder<ServerCommandSource> permCommand() {
		return CommandManager.literal("perm")
				.executes(InstantLore::permList)
				.then(CommandManager.literal("list").executes(InstantLore::permList))
				.then(CommandManager.literal("grant")
						.then(CommandManager.argument("команда", StringArgumentType.word())
								.suggests((ctx, builder) -> net.minecraft.command.CommandSource
										.suggestMatching(
												com.rufana.instantlore.server.LorePerms.known(), builder))
								.executes(ctx -> permSet(ctx, true))))
				.then(CommandManager.literal("revoke")
						.then(CommandManager.argument("команда", StringArgumentType.word())
								.suggests((ctx, builder) -> net.minecraft.command.CommandSource
										.suggestMatching(
												com.rufana.instantlore.server.LorePerms.known(), builder))
								.executes(ctx -> permSet(ctx, false))))
				.then(CommandManager.literal("reset").executes(InstantLore::permReset));
	}

	private static int permList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		boolean owner = com.rufana.instantlore.server.LorePerms.isOwner(source);

		List<String> open = new java.util.ArrayList<>();
		List<String> granted = new java.util.ArrayList<>();
		List<String> closed = new java.util.ArrayList<>();

		for (String name : com.rufana.instantlore.server.LorePerms.known()) {
			if (com.rufana.instantlore.server.LorePerms.OWNER_ONLY.contains(name)) {
				continue;
			}
			if (com.rufana.instantlore.server.LorePerms.ALWAYS_OPEN.contains(name)) {
				open.add(name);
			} else if (com.rufana.instantlore.server.LorePerms.isGranted(name)) {
				granted.add(name);
			} else {
				closed.add(name);
			}
		}

		StringBuilder text = new StringBuilder("Права /lore для админов:");
		text.append("\n  открыто всегда: ").append(String.join(", ", open));
		text.append("\n  выдано: ").append(granted.isEmpty() ? "—" : String.join(", ", granted));
		text.append("\n  закрыто: ").append(closed.isEmpty() ? "—" : String.join(", ", closed));
		text.append("\n  Закрытого нет в подсказке — это не поломка. Нужна команда — просить у ")
				.append(TECH_ADMIN).append(".");
		if (owner) {
			text.append("\n  Выдать: /lore perm grant <команда>; снять: revoke; всё снять: reset.");
			text.append("\n  Не выдаётся никогда: ")
					.append(String.join(", ", new java.util.TreeSet<>(
							com.rufana.instantlore.server.LorePerms.OWNER_ONLY)));
		}

		ilReply(source, () -> Text.literal(text.toString()), false);
		return 1;
	}

	private static int permSet(CommandContext<ServerCommandSource> ctx, boolean grant) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		String name = StringArgumentType.getString(ctx, "команда");

		if (!com.rufana.instantlore.server.LorePerms.isKnown(name)) {
			ilReplyErr(source, Text.literal(
					"Команды /lore " + name + " нет. /lore perm list — что существует."));
			return 0;
		}

		if (com.rufana.instantlore.server.LorePerms.OWNER_ONLY.contains(name)) {
			ilReplyErr(source, Text.literal(
					"/lore " + name + " не выдаётся: это ручка техадмина, а не админская."));
			return 0;
		}

		if (com.rufana.instantlore.server.LorePerms.ALWAYS_OPEN.contains(name)) {
			ilReplyErr(source, Text.literal(
					"/lore " + name + " и так открыта всем админам — выдавать нечего."));
			return 0;
		}

		net.minecraft.server.MinecraftServer server = source.getServer();
		boolean changed = grant
				? com.rufana.instantlore.server.LorePerms.grant(name, server)
				: com.rufana.instantlore.server.LorePerms.revoke(name, server);

		if (!changed) {
			ilReply(source, () -> Text.literal("/lore " + name + " и так "
					+ (grant ? "выдана." : "закрыта.")), false);
			return 0;
		}

		ilReply(source, () -> Text.literal(grant
				? "/lore " + name + " открыта всем админам."
				: "/lore " + name + " закрыта для админов."), false);
		return 1;
	}

	private static int permReset(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		int removed = com.rufana.instantlore.server.LorePerms.reset(source.getServer());
		ilReply(source, () -> Text.literal(removed == 0
				? "Выдач и не было — админам открыто только базовое."
				: "Снято выдач: " + removed + ". Осталось базовое."), false);
		return 1;
	}

	private static int sourcesStatus(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		boolean signed = ContentSources.document() != null;

		ilReply(source, () -> Text.literal(
				"Источники (serial " + ContentSources.serial() + ", "
						+ (signed ? "подписанный список" : "посевной список из мода") + "):\n"
						+ String.join("\n", ContentSources.current())), false);
		return 1;
	}

	private static int sourcesReload(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		boolean changed = ContentSources.loadFromDisk();

		if (!changed && ContentSources.document() == null) {
			ilReplyErr(source, Text.literal("Список с диска не принят — причина в логе сервера. "
					+ "Ждём instantlore/sources.json и instantlore/sources.sig"));
			return 0;
		}

		int sent = broadcastSources(source.getServer());

		ilReply(source, () -> Text.literal(
				(changed ? "Список принят" : "Файл не новее уже принятого")
						+ " (serial " + ContentSources.serial()
						+ "), разослан игрокам: " + sent), false);
		return 1;
	}

	private static int sourcesPush(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();

		if (!checkTechAdmin(source)) {
			return 0;
		}

		if (ContentSources.document() == null) {
			ilReplyErr(source, Text.literal(
					"Рассылать нечего: подписанного списка нет, работаем на посевном"));
			return 0;
		}

		int sent = broadcastSources(source.getServer());
		ilReply(source, () -> Text.literal("Список разослан игрокам: " + sent), false);
		return 1;
	}

	private static int broadcastSources(net.minecraft.server.MinecraftServer server) {
		int sent = 0;

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (sendSources(player)) {
				sent++;
			}
		}

		return sent;
	}

	private static boolean sendSources(ServerPlayerEntity player) {
		byte[][] document = ContentSources.document();

		if (document == null) {
			return false;
		}

		java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
		com.google.gson.JsonObject data = new com.google.gson.JsonObject();
		data.addProperty("doc", encoder.encodeToString(document[0]));
		data.addProperty("sig", encoder.encodeToString(document[1]));

		ServerPlayNetworking.send(player, new LoreEventPayload("lore.sources", data.toString()));
		return true;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> reloadCommand() {
		return CommandManager.literal("reload").executes(ctx -> {
			ServerCommandSource source = ctx.getSource();
			ilReply(source, () -> Text.literal("Читаю манифест..."), false);

			ServerContentLoader.reload(source.getServer(), () -> {
				String version = ServerContentState.version();
				broadcastVersion(source.getServer(), version);
				ilReply(source, () -> Text.literal("Контент версии " + version), false);
			});

			com.rufana.instantlore.server.ClientVersions.refreshTarget();
			return 1;
		});
	}

	private static void broadcastVersion(net.minecraft.server.MinecraftServer server, String version) {
		ContentVersionPayload payload = new ContentVersionPayload(version);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static LiteralArgumentBuilder<ServerCommandSource> probeCommand() {
		return CommandManager.literal("probe")
				.then(CommandManager.literal("teleport")
						.executes(InstantLore::probeLimboTeleport))
				.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
						.executes(ctx -> {
							BlockPos at = BlockPosArgumentType.getBlockPos(ctx, "at");
							net.minecraft.server.world.ServerWorld world =
									ctx.getSource().getWorld();

							ilReply(ctx.getSource(), () -> Text.literal(
									LorePathfinder.probe(world, at)), false);
							return 1;
						}));
	}

	private static int probeLimboTeleport(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Зонд работает от лица игрока"));
			return 0;
		}
		var server = ctx.getSource().getServer();
		boolean inLimbo = player.getEntityWorld().getRegistryKey()
				.equals(com.rufana.instantlore.server.LimboTransfer.LIMBO_KEY);
		net.minecraft.server.world.ServerWorld target = inLimbo
				? server.getWorld(net.minecraft.world.World.OVERWORLD)
				: server.getWorld(com.rufana.instantlore.server.LimboTransfer.LIMBO_KEY);
		if (target == null) {
			ilReplyErr(ctx.getSource(), Text.literal("Целевое измерение не найдено"));
			return 0;
		}
		net.minecraft.util.math.Vec3d before = player.getEntityPos();
		com.rufana.instantlore.probe.waystones.TeleportProbe.teleport(player, target,
				new net.minecraft.util.math.Vec3d(before.x, inLimbo ? 100.0 : 2.0, before.z));

		var world = player.getEntityWorld().getRegistryKey().getValue();
		ilReply(ctx.getSource(), () -> Text.literal("Зонд: попытка "
				+ (inLimbo ? "из лимбо наружу" : "снаружи в лимбо")
				+ " — игрок сейчас в " + world), false);
		com.rufana.instantlore.server.LoreDevLog.info("[probe] limbo border crossing: {} → {}",
				inLimbo ? "limbo" : "world", world);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> walkCommand() {
		return CommandManager.literal("walk")
				.then(CommandManager.literal("stop")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> {
									for (ServerPlayerEntity player
											: EntityArgumentType.getPlayers(ctx, "targets")) {
										PathRunner.cancel(player);
									}
									return 1;
								})))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("goal", BlockPosArgumentType.blockPos())
								.executes(ctx -> walk(ctx, "walk"))
								.then(CommandManager.argument("speed", StringArgumentType.word())
										.executes(ctx -> walk(ctx,
												StringArgumentType.getString(ctx, "speed"))))));
	}

	private static int walk(CommandContext<ServerCommandSource> ctx, String speed)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BlockPos goal = BlockPosArgumentType.getBlockPos(ctx, "goal");
		int count = 0;

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			PathRunner.walkTo(player, goal, speed);
			count++;
		}

		int sent = count;
		ilReply(ctx.getSource(), () -> Text.literal(
				"Веду игроков (" + sent + ") в " + goal.toShortString()), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> taskCommand() {
		return CommandManager.literal("task")
				.then(CommandManager.literal("stop")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> {
									for (ServerPlayerEntity player
											: EntityArgumentType.getPlayers(ctx, "targets")) {
										TaskRunner.cancel(player, "команда оператора");
									}
									return 1;
								})))
				.then(CommandManager.argument("targets", EntityArgumentType.players())
						.then(CommandManager.argument("steps", StringArgumentType.greedyString())
								.executes(InstantLore::startTask)));
	}

	private static int startTask(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String source = StringArgumentType.getString(ctx, "steps");
		List<TaskStep> steps;

		try {
			steps = TaskParser.parse(source);
		} catch (RuntimeException e) {
			ilReplyErr(ctx.getSource(), Text.literal("Задача не разобрана: " + e.getMessage()));
			return 0;
		}

		for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "targets")) {
			TaskRunner.start(player, TaskParser.parse(source));
		}

		int count = steps.size();
		ilReply(ctx.getSource(), () -> Text.literal("Задача запущена, шагов: " + count), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> ritualCommand() {
		return CommandManager.literal("ritual")
				.then(CommandManager.literal("stop")
						.then(CommandManager.argument("targets", EntityArgumentType.players())
								.executes(ctx -> {
									for (ServerPlayerEntity player
											: EntityArgumentType.getPlayers(ctx, "targets")) {
										com.rufana.instantlore.server.RitualRunner.cancel(
												player, "команда оператора");
									}
									return 1;
								})))
				.then(CommandManager.argument("цель", EntityArgumentType.player())
						.executes(ctx -> {
							ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
							boolean started =
									com.rufana.instantlore.server.RitualRunner.start(target);

							ilReply(ctx.getSource(), () -> Text.literal(started
									? "Ритуал запущен: " + target.getName().getString()
									: "Ритуал уже идёт у " + target.getName().getString()), false);
							return started ? 1 : 0;
						}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> geoCommand() {
		return CommandManager.literal("geo")
				.then(CommandManager.literal("start")
						.executes(ctx -> geoStart(ctx, null))
						.then(CommandManager.argument("ростер",
										com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.suggests((ctx, builder) -> {
									String rem = builder.getRemaining();
									int sp = rem.lastIndexOf(' ');
									com.mojang.brigadier.suggestion.SuggestionsBuilder b =
											sp >= 0 ? builder.createOffset(builder.getStart() + sp + 1) : builder;
									java.util.List<String> opts =
											new java.util.ArrayList<>(ctx.getSource().getPlayerNames());
									opts.add("@a");
									opts.addAll(GEO.difficultyNames());
									return net.minecraft.command.CommandSource.suggestMatching(opts, b);
								})
								.executes(ctx -> geoStart(ctx,
										com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ростер")))))
				.then(CommandManager.literal("stop")
						.executes(ctx -> {
							boolean ok = GEO.stop();
							ilReply(ctx.getSource(), () -> Text.literal(ok
									? "Геогессер остановлен" : "Матч не идёт"), false);
							return ok ? 1 : 0;
						}));
	}

	private static int geoStart(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
			String roster) {
		ServerCommandSource src = ctx.getSource();
		ServerPlayerEntity host = src.getPlayer();
		net.minecraft.server.MinecraftServer srv = src.getServer();
		java.util.List<ServerPlayerEntity> players = new java.util.ArrayList<>();
		String difficulty = null;

		if (roster != null && !roster.isBlank()) {
			String[] toks = roster.trim().split("\\s+");
			int n = toks.length;
			if (n > 0 && GEO.hasDifficulty(toks[n - 1])) {
				difficulty = toks[n - 1];
				n--;
			}
			java.util.List<String> missing = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				String name = toks[i];
				if (name.equalsIgnoreCase("@a")) {
					for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
						if (!players.contains(p)) {
							players.add(p);
						}
					}
					continue;
				}
				ServerPlayerEntity p = findOnline(srv, name);
				if (p == null) {
					missing.add(name);
				} else if (!players.contains(p)) {
					players.add(p);
				}
			}
			if (!missing.isEmpty()) {
				String m = String.join(", ", missing);
				ilReply(src, () -> Text.literal("Не в сети / нет игрока: " + m), false);
				return 0;
			}
		}
		if (players.isEmpty() && host != null) {
			players.add(host);
		}
		if (players.isEmpty()) {
			ilReply(src, () -> Text.literal("Некого запускать (укажи ники или @a)"), false);
			return 0;
		}
		boolean ok = GEO.start(host, players, difficulty);
		int size = players.size();
		ilReply(src, () -> Text.literal(ok
				? "Геогессер запущен: " + size
				: "Не запущен (матч уже идёт, или источник s1/измерение не готовы)"), false);
		return ok ? 1 : 0;
	}

	private static ServerPlayerEntity findOnline(net.minecraft.server.MinecraftServer srv, String name) {
		ServerPlayerEntity p = srv.getPlayerManager().getPlayer(name);
		if (p != null) {
			return p;
		}
		for (ServerPlayerEntity q : srv.getPlayerManager().getPlayerList()) {
			if (q.getName().getString().equalsIgnoreCase(name)) {
				return q;
			}
		}
		return null;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> riftCommand() {
		return CommandManager.literal("rift")
				.then(CommandManager.literal("open")
						.then(CommandManager.argument("posA", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("posB", BlockPosArgumentType.blockPos())
										.executes(ctx -> riftOpen(ctx, false)))
								.then(CommandManager.literal("to")
										.then(CommandManager.argument("мир", DimensionArgumentType.dimension())
												.then(CommandManager.argument("posB", BlockPosArgumentType.blockPos())
														.executes(ctx -> riftOpen(ctx, true)))))))
				.then(CommandManager.literal("marker")
						.executes(ctx -> riftMarker(ctx, null))
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.executes(ctx -> riftMarker(ctx,
										BlockPosArgumentType.getBlockPos(ctx, "поз")))))
				.then(CommandManager.literal("close")
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(InstantLore::riftClose)))
				.then(CommandManager.literal("list")
						.executes(InstantLore::riftList));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> offeringCommand() {
		return CommandManager.literal("offering")
				.then(CommandManager.literal("set")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("предмет",
												net.minecraft.command.argument.IdentifierArgumentType.identifier())
										.then(CommandManager.argument("сколько", IntegerArgumentType.integer(1))
												.executes(ctx -> offeringSet(ctx, null))
												.then(CommandManager.argument("событие", StringArgumentType.word())
														.executes(ctx -> offeringSet(ctx,
																StringArgumentType.getString(ctx, "событие"))))))))
				.then(CommandManager.literal("any")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("сколько", IntegerArgumentType.integer(1))
										.then(CommandManager.argument("что", StringArgumentType.greedyString())
												.executes(InstantLore::offeringAny)))))
				.then(CommandManager.literal("preset")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("имя", StringArgumentType.word())
										.executes(InstantLore::offeringPreset))))
				.then(CommandManager.literal("title")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.argument("текст", StringArgumentType.greedyString())
										.executes(InstantLore::offeringTitle))))
				.then(CommandManager.literal("line")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.literal("clear")
										.executes(InstantLore::offeringLineClear))
								.then(CommandManager.literal("add")
										.then(CommandManager.argument("текст", StringArgumentType.greedyString())
												.executes(InstantLore::offeringLineAdd)))
								.then(CommandManager.literal("remove")
										.then(CommandManager.argument("номер", IntegerArgumentType.integer(1))
												.executes(InstantLore::offeringLineRemove)))
								.then(CommandManager.literal("set")
										.then(CommandManager.argument("номер", IntegerArgumentType.integer(1))
												.then(CommandManager.argument("текст", StringArgumentType.greedyString())
														.executes(InstantLore::offeringLineSet))))))
				.then(CommandManager.literal("progress")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.literal("on")
										.executes(ctx -> offeringProgress(ctx, true)))
								.then(CommandManager.literal("off")
										.executes(ctx -> offeringProgress(ctx, false)))))
				.then(CommandManager.literal("score")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.executes(InstantLore::offeringScore)))
				.then(CommandManager.literal("fill")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.executes(InstantLore::offeringFill)))
				.then(CommandManager.literal("info")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.executes(InstantLore::offeringInfo)))
				.then(CommandManager.literal("clear")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.executes(InstantLore::offeringClear)))
				.then(CommandManager.literal("list")
						.executes(InstantLore::offeringList));
	}

	private static int offeringSet(CommandContext<ServerCommandSource> ctx, String event)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.define(source.getWorld(), pos,
				net.minecraft.command.argument.IdentifierArgumentType
						.getIdentifier(ctx, "предмет").toString(),
				IntegerArgumentType.getInteger(ctx, "сколько"), event);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Жертвенник: " + OFFERING.info(source.getWorld(), pos)), false);
		return 1;
	}

	private static int offeringAny(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.define(source.getWorld(), pos,
				StringArgumentType.getString(ctx, "что"),
				IntegerArgumentType.getInteger(ctx, "сколько"), null);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Жертвенник: " + OFFERING.info(source.getWorld(), pos)), false);
		return 1;
	}

	private static int offeringPreset(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.applyPreset(source.getWorld(), pos,
				StringArgumentType.getString(ctx, "имя"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Жертвенник: " + OFFERING.info(source.getWorld(), pos)), false);
		return 1;
	}

	private static int offeringTitle(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.setTitle(source.getWorld(), pos,
				StringArgumentType.getString(ctx, "текст"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Жертвенник: " + OFFERING.info(source.getWorld(), pos)), false);
		return 1;
	}

	private static int offeringLineAdd(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.addLine(source.getWorld(), pos,
				StringArgumentType.getString(ctx, "текст"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return offeringLines(source, pos);
	}

	private static int offeringLineRemove(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.removeLine(source.getWorld(), pos,
				IntegerArgumentType.getInteger(ctx, "номер"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return offeringLines(source, pos);
	}

	private static int offeringLineSet(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.setLine(source.getWorld(), pos,
				IntegerArgumentType.getInteger(ctx, "номер"),
				StringArgumentType.getString(ctx, "текст"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return offeringLines(source, pos);
	}

	private static int offeringLines(ServerCommandSource source, BlockPos pos) {
		java.util.List<String> lines = OFFERING.lines(source.getWorld(), pos);
		if (lines == null || lines.isEmpty()) {
			ilReply(source, () -> Text.literal("Строк текста нет"), false);
			return 1;
		}
		MutableText out = Text.literal("Строки надписи:");
		for (int i = 0; i < lines.size(); i++) {
			out.append(Text.literal("\n" + (i + 1) + ". " + lines.get(i)));
		}
		ilReply(source, () -> out, false);
		return 1;
	}

	private static int offeringLineClear(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.clearLines(source.getWorld(), pos);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Строки убраны"), false);
		return 1;
	}

	private static int offeringProgress(CommandContext<ServerCommandSource> ctx, boolean on)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.setProgress(source.getWorld(), pos, on);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal(on ? "Счёт показан" : "Счёт скрыт"), false);
		return 1;
	}

	private static int offeringScore(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "поз");
		java.util.List<String> rows = OFFERING.score(source.getWorld(), pos);

		if (rows == null) {
			ilReplyErr(source, Text.literal("Здесь жертвенник не настроен"));
			return 0;
		}
		if (rows.isEmpty()) {
			ilReply(source, () -> Text.literal("Сюда ещё никто ничего не принёс"), false);
			return 1;
		}

		MutableText out = Text.literal(OFFERING.info(source.getWorld(), pos));
		for (String row : rows) {
			out.append(Text.literal("\n" + row));
		}
		ilReply(source, () -> out, false);
		return 1;
	}

	private static int offeringFill(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = OFFERING.fill(source.getWorld(), pos, source.getPlayer());

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Досыпано: " + OFFERING.info(source.getWorld(), pos)), false);
		return 1;
	}

	private static int offeringInfo(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "поз");
		String info = OFFERING.info(source.getWorld(), pos);

		if (info == null) {
			ilReplyErr(source, Text.literal("Здесь жертвенник не настроен"));
			return 0;
		}
		ilReply(source, () -> Text.literal(info), false);
		return 1;
	}

	private static int offeringClear(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "поз");

		if (!OFFERING.clear(source.getWorld(), pos)) {
			ilReplyErr(source, Text.literal("Здесь жертвенник не настроен"));
			return 0;
		}
		ilReply(source, () -> Text.literal("Жертвенник снят"), false);
		return 1;
	}

	private static int offeringList(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = OFFERING.list();
		if (lines.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Жертвенников нет"), false);
			return 1;
		}
		MutableText out = Text.literal("Жертвенники (" + lines.size() + "):");
		for (String line : lines) {
			out.append(Text.literal("\n" + line));
		}
		ilReply(ctx.getSource(), () -> out, false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> tourCommand() {
		return CommandManager.literal("tour")
				.then(CommandManager.literal("start")
						.then(CommandManager.argument("цель", EntityArgumentType.player())
								.executes(ctx -> tourStart(ctx, null))
								.then(CommandManager.argument("показ", StringArgumentType.word())
										.executes(ctx -> tourStart(ctx,
												StringArgumentType.getString(ctx, "показ"))))))
				.then(CommandManager.literal("stop")
						.then(CommandManager.argument("цель", EntityArgumentType.player())
								.executes(InstantLore::tourStop)))
				.then(CommandManager.literal("list")
						.executes(InstantLore::tourList))
				.then(CommandManager.literal("moment")
						.executes(InstantLore::tourMoments)
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.executes(InstantLore::tourMoment)))
				.then(CommandManager.literal("seen")
						.executes(InstantLore::tourSeen)
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("ник", StringArgumentType.word())
										.executes(ctx -> tourSeenSet(ctx, true))))
						.then(CommandManager.literal("remove")
								.then(CommandManager.argument("ник", StringArgumentType.word())
										.executes(ctx -> tourSeenSet(ctx, false)))));
	}

	private static int tourStart(CommandContext<ServerCommandSource> ctx, String tour)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
		String problem = TOUR.start(target, tour, true);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Показ начат: " + target.getName().getString()), false);
		return 1;
	}

	private static int tourStop(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "цель");
		boolean stopped = TOUR.stop(source.getServer(), target.getUuid());

		ilReply(source, () -> Text.literal(stopped
				? "Показ оборван, игрок возвращён"
				: "Этот игрок ничего не смотрит"), false);
		return stopped ? 1 : 0;
	}

	private static int tourList(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = TOUR.list(ctx.getSource().getServer());
		if (lines.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Сейчас никто не смотрит"), false);
			return 1;
		}
		MutableText out = Text.literal("Смотрят (" + lines.size() + "):");
		for (String line : lines) {
			out.append(Text.literal("\n" + line));
		}
		ilReply(ctx.getSource(), () -> out, false);
		return 1;
	}

	private static int tourMoment(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		String problem = TOUR.fire(source.getServer(), name, "");

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal("Момент «" + name + "» объявлен"), false);
		return 1;
	}

	private static int tourMoments(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = TOUR.moments();
		if (lines.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Моменты не заданы"), false);
			return 1;
		}
		MutableText out = Text.literal("Моменты (" + lines.size() + "):");
		for (String line : lines) {
			out.append(Text.literal("\n" + line));
		}
		ilReply(ctx.getSource(), () -> out, false);
		return 1;
	}

	private static int tourSeen(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> names = TOUR.initiates();
		if (names.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Посвящённых пока нет"), false);
			return 1;
		}
		MutableText out = Text.literal("Посвящённых (" + names.size() + "): "
				+ String.join(", ", names));
		ilReply(ctx.getSource(), () -> out, false);
		return 1;
	}

	private static int tourSeenSet(CommandContext<ServerCommandSource> ctx, boolean member) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "ник");
		ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(name);
		java.util.UUID uuid = online != null ? online.getUuid()
				: java.util.UUID.nameUUIDFromBytes(
						("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));

		boolean changed = TOUR.setInitiate(uuid, name, member);
		ilReply(source, () -> Text.literal(changed
				? (member ? "Посвящён: " : "Выписан: ") + name
				: (member ? name + " уже в списке" : name + " и так не в списке")), false);
		return changed ? 1 : 0;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> riteCommand() {
		return CommandManager.literal("rite")
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
										.executes(ctx -> riteAdd(ctx, false))
										.then(CommandManager.literal("secret")
												.executes(ctx -> riteAdd(ctx, true)))
										.then(CommandManager.literal("core")
												.executes(ctx -> riteAdd(ctx, false))))))
				.then(CommandManager.literal("remove")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
										.executes(InstantLore::riteRemove))))
				.then(CommandManager.literal("gate")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
										.executes(InstantLore::riteGate))))
				.then(CommandManager.literal("event")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.then(CommandManager.literal("core")
										.then(CommandManager.argument("тип", StringArgumentType.word())
												.executes(ctx -> riteEvent(ctx, false))))
								.then(CommandManager.literal("secret")
										.then(CommandManager.argument("тип", StringArgumentType.word())
												.executes(ctx -> riteEvent(ctx, true))))))
				.then(CommandManager.literal("storm")
						.then(CommandManager.argument("поз", BlockPosArgumentType.blockPos())
								.then(CommandManager.literal("off")
										.executes(ctx -> riteStorm(ctx, 0, 1, 1)))
								.then(CommandManager.argument("радиус", IntegerArgumentType.integer(1))
										.executes(ctx -> riteStorm(ctx,
												IntegerArgumentType.getInteger(ctx, "радиус"), 1, 1))
										.then(CommandManager.argument("высота", IntegerArgumentType.integer(-16, 64))
												.executes(ctx -> riteStorm(ctx,
														IntegerArgumentType.getInteger(ctx, "радиус"),
														IntegerArgumentType.getInteger(ctx, "высота"), 1))
												.then(CommandManager.argument("период", IntegerArgumentType.integer(1, 200))
														.executes(ctx -> riteStorm(ctx,
																IntegerArgumentType.getInteger(ctx, "радиус"),
																IntegerArgumentType.getInteger(ctx, "высота"),
																IntegerArgumentType.getInteger(ctx, "период"))))))))
				.then(CommandManager.literal("need")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.then(CommandManager.argument("сколько", IntegerArgumentType.integer(0))
										.executes(InstantLore::riteNeed))))
				.then(CommandManager.literal("reset")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.executes(InstantLore::riteReset)))
				.then(CommandManager.literal("drop")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.executes(InstantLore::riteDrop)))
				.then(CommandManager.literal("info")
						.then(CommandManager.argument("имя", StringArgumentType.word())
								.executes(InstantLore::riteInfo)))
				.then(CommandManager.literal("list")
						.executes(InstantLore::riteList));
	}

	private static int riteAdd(CommandContext<ServerCommandSource> ctx, boolean secret)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "поз");
		String problem = RITE.add(source.getWorld(), pos, name, secret);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, (secret ? "Тайный" : "Основной") + " жертвенник вписан в «" + name + "»");
	}

	private static int riteRemove(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		String problem = RITE.remove(source.getWorld(),
				BlockPosArgumentType.getBlockPos(ctx, "поз"), name);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, "Жертвенник выписан из «" + name + "»");
	}

	private static int riteGate(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		String problem = RITE.setGate(source.getWorld(),
				BlockPosArgumentType.getBlockPos(ctx, "поз"), name);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, "Точка врат задана");
	}

	private static int riteEvent(CommandContext<ServerCommandSource> ctx, boolean secret) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		String problem = RITE.setEvent(name, secret, StringArgumentType.getString(ctx, "тип"));

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, "Событие задано");
	}

	private static int riteNeed(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		int count = IntegerArgumentType.getInteger(ctx, "сколько");
		String problem = RITE.setQuorum(name, count);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, count == 0
				? "Исход снова требует все основные жертвенники"
				: "Для исхода довольно любых " + count);
	}

	private static int riteStorm(CommandContext<ServerCommandSource> ctx, int radius, int height, int period)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		String problem = RITE.setStorm(source.getWorld(),
				BlockPosArgumentType.getBlockPos(ctx, "поз"), radius, height, period);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		ilReply(source, () -> Text.literal(radius <= 0
				? "Зов молнией снят"
				: "Зовёт: радиус " + radius + ", +" + height + " по высоте, раз в " + period + " т."), false);
		return 1;
	}

	private static int riteReset(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");
		String problem = RITE.reset(source.getServer(), name);

		if (problem != null) {
			ilReplyErr(source, Text.literal(problem));
			return 0;
		}
		return riteReport(source, name, "«" + name + "» сброшен, врата закрыты");
	}

	private static int riteDrop(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "имя");

		if (!RITE.drop(source.getServer(), name)) {
			ilReplyErr(source, Text.literal("нет такого обряда"));
			return 0;
		}
		ilReply(source, () -> Text.literal("Обряд «" + name + "» снесён (жертвенники остались)"), false);
		return 1;
	}

	private static int riteInfo(CommandContext<ServerCommandSource> ctx) {
		return riteReport(ctx.getSource(), StringArgumentType.getString(ctx, "имя"), null);
	}

	private static int riteReport(ServerCommandSource source, String name, String headline) {
		java.util.List<String> rows = RITE.info(source.getServer(), name);
		if (rows == null) {
			ilReplyErr(source, Text.literal("нет такого обряда"));
			return 0;
		}
		MutableText out = Text.literal(headline == null ? "" : headline);
		for (String row : rows) {
			out.append(Text.literal((headline == null && out.getString().isEmpty() ? "" : "\n") + row));
		}
		ilReply(source, () -> out, false);
		return 1;
	}

	private static int riteList(CommandContext<ServerCommandSource> ctx) {
		java.util.List<String> lines = RITE.list();
		if (lines.isEmpty()) {
			ilReply(ctx.getSource(), () -> Text.literal("Обрядов нет"), false);
			return 1;
		}
		MutableText out = Text.literal("Обряды (" + lines.size() + "):");
		for (String line : lines) {
			out.append(Text.literal("\n" + line));
		}
		ilReply(ctx.getSource(), () -> out, false);
		return 1;
	}

	private static net.minecraft.util.math.Vec3d blockCenter(BlockPos pos) {
		return new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static int riftOpen(CommandContext<ServerCommandSource> ctx, boolean cross)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		net.minecraft.server.world.ServerWorld worldA = source.getWorld();
		net.minecraft.util.math.Vec3d posA = blockCenter(BlockPosArgumentType.getBlockPos(ctx, "posA"));

		net.minecraft.server.world.ServerWorld worldB = cross
				? DimensionArgumentType.getDimensionArgument(ctx, "мир")
				: worldA;
		net.minecraft.util.math.Vec3d posB = blockCenter(BlockPosArgumentType.getBlockPos(ctx, "posB"));

		java.util.UUID owner = source.getEntity() instanceof ServerPlayerEntity player
				? player.getUuid()
				: net.minecraft.util.Util.NIL_UUID;

		String id = com.rufana.instantlore.server.Rifts.openPair(
				worldA.getRegistryKey(), posA, worldB.getRegistryKey(), posB, owner);

		ilReply(source, () -> Text.literal("Разлом открыт: " + id + " ("
				+ worldA.getRegistryKey().getValue() + " ↔ " + worldB.getRegistryKey().getValue()
				+ "). Закрыть: /lore rift close " + id), false);
		return 1;
	}

	private static int riftMarker(CommandContext<ServerCommandSource> ctx, BlockPos pos) {
		ServerCommandSource source = ctx.getSource();
		net.minecraft.server.world.ServerWorld world = source.getWorld();
		net.minecraft.util.math.Vec3d at = pos == null ? source.getPosition() : blockCenter(pos);

		String id = com.rufana.instantlore.server.Rifts.openMarker(world.getRegistryKey(), at);

		ilReply(source, () -> Text.literal("Маркер-разлом поставлен: " + id
				+ ". Закрыть: /lore rift close " + id), false);
		return 1;
	}

	private static int riftClose(CommandContext<ServerCommandSource> ctx) {
		String id = StringArgumentType.getString(ctx, "id");
		if (com.rufana.instantlore.server.Rifts.close(id)) {
			ilReply(ctx.getSource(),
					() -> Text.literal("Разлом закрыт: " + id + " (с парным концом)"), false);
			return 1;
		}
		ilReplyErr(ctx.getSource(), Text.literal("Разлом не найден: " + id));
		return 0;
	}

	private static int riftList(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity asker = source.getPlayer();
		net.minecraft.registry.RegistryKey<net.minecraft.world.World> from = asker != null
				? asker.getEntityWorld().getRegistryKey()
				: net.minecraft.world.World.OVERWORLD;
		net.minecraft.util.math.Vec3d origin = asker != null
				? asker.getEntityPos()
				: net.minecraft.util.math.Vec3d.ZERO;

		java.util.List<com.rufana.instantlore.server.Rifts.RiftLine> lines =
				com.rufana.instantlore.server.Rifts.describe(from, origin);
		if (lines.isEmpty()) {
			ilReply(source, () -> Text.literal("Разломов нет"), false);
			return 1;
		}
		MutableText out = Text.literal("Разломы (" + lines.size() + ") — ближние сверху, [id] копирует:");
		for (com.rufana.instantlore.server.Rifts.RiftLine line : lines) {
			out.append(Text.literal("\n")).append(riftDistanceText(line.distance()))
					.append(riftEndText(line.a()));
			if (line.b() != null) {
				out.append(grey(" ↔ ")).append(riftEndText(line.b()));
			}
		}
		ilReply(source, () -> out, false);
		return 1;
	}

	private static MutableText riftDistanceText(double distance) {
		String text = distance < 0 ? "—" : (Math.round(distance) + " бл");
		return Text.literal(String.format("%-8s", text))
				.styled(s -> s.withColor(net.minecraft.util.Formatting.DARK_GRAY));
	}

	private static MutableText riftEndText(com.rufana.instantlore.server.Rifts.RiftEnd end) {
		MutableText button = Text.literal("[id]").styled(s -> s
				.withColor(net.minecraft.util.Formatting.AQUA)
				.withClickEvent(new net.minecraft.text.ClickEvent.CopyToClipboard(end.id()))
				.withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(
						Text.literal("Копировать id: " + end.id()))));
		return button.append(grey(" " + end.where()));
	}

	private static MutableText grey(String s) {
		return Text.literal(s).styled(st -> st.withColor(net.minecraft.util.Formatting.GRAY));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> versionCommand() {
		return CommandManager.literal("version")
				.then(CommandManager.argument("value", StringArgumentType.word())
						.executes(ctx -> {
							String value = StringArgumentType.getString(ctx, "value");
							ServerContentState.setVersion(value);

							broadcastVersion(ctx.getSource().getServer(), value);

							ilReply(ctx.getSource(),
									() -> Text.literal("Версия контента → " + value), false);
							return 1;
						}));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> versionsCommand() {
		return CommandManager.literal("versions")
				.executes(ctx -> reportVersions(ctx, null))
				.then(CommandManager.argument("эталон", StringArgumentType.word())
						.executes(ctx ->
								reportVersions(ctx, StringArgumentType.getString(ctx, "эталон"))));
	}

	private static int reportVersions(CommandContext<ServerCommandSource> ctx, String explicitTarget) {
		ServerCommandSource source = ctx.getSource();
		String target = explicitTarget != null
				? explicitTarget
				: ClientVersions.target();

		var players = source.getServer().getPlayerManager().getPlayerList();

		StringBuilder sb = new StringBuilder();
		sb.append(target == null
				? "Версии клиента (эталон неизвестен — client-version.json не выложен):"
				: "Версии клиента (эталон " + target + "):");

		if (players.isEmpty()) {
			sb.append("\n  никого нет онлайн");
		}

		int updated = 0;
		int pending = 0;
		int staged = 0;

		for (ServerPlayerEntity player : players) {
			String name = player.getName().getString();
			String reported = ClientVersions.reported(player.getUuid());
			ClientVersions.UpdateState state = ClientVersions.updateState(player.getUuid());

			boolean waitingRestart = state != null && state.stagedVersion() != null;

			String mark;
			if (target == null) {
				mark = "";
			} else if (reported == null) {
				mark = "? ";
				pending++;
			} else if (reported.equals(target)) {
				mark = "✔ ";
				updated++;
			} else if (state != null && state.swapFailing()) {
				mark = "⛔ ";
				pending++;
			} else if (waitingRestart) {
				mark = "⤓ ";
				staged++;
			} else {
				mark = "✗ ";
				pending++;
			}

			sb.append("\n  ").append(mark).append(name)
					.append(" — ").append(reported == null ? "нет доклада" : reported)
					.append(stagingNote(reported, state, target))
					.append(diagNote(player.getUuid(), mark));
		}

		if (target != null && !players.isEmpty()) {
			sb.append("\nОбновились: ").append(updated).append('/').append(players.size())
					.append(". Скачали, ждут перезапуска игры: ").append(staged)
					.append(". Ещё нет: ").append(pending).append('.');
		}

		feedbackLines(source, sb.toString());
		return updated;
	}

	private static String stagingNote(String reported, ClientVersions.UpdateState state, String target) {
		if (reported == null) {
			return "";
		}
		if (state == null) {
			return " (о загрузке не докладывает — клиент старше 0.72.0)";
		}
		if (state.swapFailing()) {
			return ", скачан " + state.stagedVersion() + ", но подмена не удалась ("
					+ state.swapAttempts() + " попытки) — jar менять руками";
		}
		if (state.stagedVersion() != null) {
			return ", скачан " + state.stagedVersion() + " — ждёт перезапуска игры";
		}
		if (!state.swappable()) {
			return " (запущен не из jar — сам не обновится)";
		}
		if (target != null && reported.equals(target)) {
			return "";
		}
		return ", новое не скачано";
	}

	private static String diagNote(java.util.UUID uuid, String mark) {
		if ("✔ ".equals(mark)) {
			return "";
		}
		ClientVersions.Diag diag = ClientVersions.diag(uuid);
		if (diag == null || diag.code() == null || diag.code().isBlank()) {
			return "";
		}
		return " [" + diag.code() + "]";
	}

	private static LiteralArgumentBuilder<ServerCommandSource> updateCommand() {
		return CommandManager.literal("update")
				.then(CommandManager.argument("ник", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.command.CommandSource
								.suggestMatching(ctx.getSource().getPlayerNames(), builder))
						.executes(ctx -> updateOfPlayer(ctx,
								StringArgumentType.getString(ctx, "ник"))));
	}

	private static int updateOfPlayer(CommandContext<ServerCommandSource> ctx, String nick) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(nick);

		if (player == null) {
			source.sendError(Text.literal("Игрок " + nick
					+ " не онлайн — доклад об обновлении живёт в сессии и офлайн его взять неоткуда"));
			return 0;
		}

		ClientVersions.Diag diag = ClientVersions.diag(player.getUuid());
		if (diag == null) {
			source.sendError(Text.literal(nick + " не докладывает о ходе автообновления — клиент "
					+ "старше 0.88.0 (проверить версию: /lore versions)"));
			return 0;
		}

		String reported = ClientVersions.reported(player.getUuid());
		ClientVersions.UpdateState state = ClientVersions.updateState(player.getUuid());

		StringBuilder sb = new StringBuilder("Автообновление ").append(nick)
				.append(" (доклад с его машины, недоверенный):");
		sb.append("\n  версия: ").append(reported == null ? "нет доклада" : reported)
				.append(", эталон: ").append(ClientVersions.target() == null
						? "неизвестен" : ClientVersions.target());
		if (state != null) {
			sb.append("\n  стейджинг: ").append(state.stagedVersion() == null
					? "пусто" : state.stagedVersion() + ", попыток подмены " + state.swapAttempts());
		}
		sb.append("\n  исход: ").append(diag.code().isBlank() ? "(ещё не было)" : diag.code());
		if (!diag.detail().isBlank()) {
			sb.append(" — ").append(diag.detail());
		}
		sb.append("\n  окружение: ").append(diag.env());
		appendTail(sb, "updater.log", diag.updaterTail());
		appendTail(sb, "swap.log (подмена jar)", diag.swapTail());

		feedbackLines(source, sb.toString());
		return 1;
	}

	private static void appendTail(StringBuilder sb, String title, String tail) {
		if (tail == null || tail.isBlank()) {
			sb.append("\n  ").append(title).append(": пусто");
			return;
		}
		String[] lines = tail.strip().split("\n");
		int from = Math.max(0, lines.length - 12);
		sb.append("\n  ").append(title).append(':');
		for (int i = from; i < lines.length; i++) {
			sb.append("\n    ").append(lines[i]);
		}
		if (from > 0) {
			sb.append("\n    (выше ещё ").append(from).append(" строк)");
		}
	}

	private static LiteralArgumentBuilder<ServerCommandSource> doctorCommand() {
		return CommandManager.literal("doctor")
				.executes(InstantLore::doctorSummary)
				.then(CommandManager.argument("ник", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.command.CommandSource
								.suggestMatching(ctx.getSource().getPlayerNames(), builder))
						.executes(ctx -> doctorOfPlayer(ctx,
								StringArgumentType.getString(ctx, "ник"))));
	}

	private static int doctorSummary(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		var players = source.getServer().getPlayerManager().getPlayerList();

		StringBuilder sb = new StringBuilder("Доктор — сводка по онлайну:");
		if (players.isEmpty()) {
			sb.append("\n  никого нет онлайн");
		}

		int silent = 0;
		for (ServerPlayerEntity player : players) {
			var passport = com.rufana.instantlore.server.Doctor.passport(player.getUuid());
			String name = player.getName().getString();
			if (passport == null) {
				silent++;
				continue;
			}
			var flagged = com.rufana.instantlore.server.Doctor.flagged(passport);
			var hidden = com.rufana.instantlore.server.Doctor.hiddenMods(player);

			sb.append("\n  ").append(name).append(": модов ").append(passport.topLevel())
					.append(" (+вложенных ")
					.append(passport.mods().size() - passport.topLevel()).append(')')
					.append(", опознано ").append(passport.hashed())
					.append(", паков ").append(passport.totalPacks());
			if (!flagged.isEmpty()) {
				sb.append(" · помечено ").append(flagged.size());
			}
			if (!hidden.isEmpty()) {
				sb.append(" · ⚠ расхождений ").append(hidden.size());
			}
			if (passport.truncated()) {
				sb.append(" (список срезан лимитом)");
			}
		}
		if (silent > 0) {
			sb.append("\n  без паспорта: ").append(silent)
					.append(" — клиент старее доктора, а не «модов нет»");
		}
		sb.append("\n  Подробнее: /lore doctor <ник>");

		feedbackLines(ctx.getSource(), sb.toString());
		return 1;
	}

	private static int doctorOfPlayer(CommandContext<ServerCommandSource> ctx, String nick) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(nick);
		if (player == null) {
			source.sendError(Text.literal("Игрок «" + nick + "» не онлайн — паспорт живёт "
					+ "в памяти до выхода"));
			return 0;
		}
		var passport = com.rufana.instantlore.server.Doctor.passport(player.getUuid());
		if (passport == null) {
			source.sendError(Text.literal(nick + ": паспорта нет. Это значит «клиент старее "
					+ "доктора», а не «модов нет»"));
			return 0;
		}

		StringBuilder sb = new StringBuilder("Доктор — ").append(nick).append(':');
		sb.append("\n  среда: MC ").append(passport.mcVersion())
				.append(", loader ").append(passport.loaderVersion())
				.append(", instantlore ").append(passport.modVersion());
		sb.append("\n  модов своими jar: ").append(passport.topLevel())
				.append(", вложенных (JiJ): ").append(passport.mods().size() - passport.topLevel())
				.append(", из них опознано по хэшу: ").append(passport.hashed());
		if (passport.truncated()) {
			sb.append("\n  ⚠ список срезан лимитом пакета — показано не всё");
		}
		if (!passport.reportsNested()) {
			sb.append("\n  ⚠ доклад старой схемы (").append(passport.schema())
					.append("): вложенных модов в нём нет по построению, поэтому расхождения "
							+ "по каналам не считаются — иначе каждая JiJ-библиотека выглядела "
							+ "бы скрытой");
		}

		var flagged = com.rufana.instantlore.server.Doctor.flagged(passport);
		if (flagged.isEmpty()) {
			sb.append("\n  помеченного нет (это не значит «чисто»: таблицы знают только "
					+ "вписанное)");
		} else {
			sb.append("\n  помечено:");
			flagged.forEach((mod, verdict) -> sb.append("\n    ").append(mod.id())
					.append(' ').append(mod.version())
					.append(mod.nested() ? " (внутри " + mod.parent() + ")" : "")
					.append(" — ").append(verdict.label()));
		}

		if (!passport.packs().isEmpty()) {
			sb.append("\n  ресурспаки (").append(passport.totalPacks()).append("):");
			for (String pack : passport.packs()) {
				sb.append("\n    ").append(pack.replace('\t', ' '));
			}
		}

		var hidden = com.rufana.instantlore.server.Doctor.hiddenMods(player);
		if (hidden.isEmpty()) {
			sb.append("\n  расхождений доклада с каналами нет");
		} else {
			sb.append("\n  ⚠ РАСХОЖДЕНИЕ (это факт, а не догадка — каналы сервер видит сам):");
			for (String item : hidden) {
				sb.append("\n    ").append(item);
			}
		}

		var channels = com.rufana.instantlore.server.Doctor.channels(player);
		sb.append("\n  объявлено каналов: ").append(channels.size());
		channels.stream().map(Object::toString).sorted()
				.forEach(channel -> sb.append("\n    ").append(channel));

		feedbackLines(source, sb.toString());
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> modsCommand() {
		return CommandManager.literal("mods")
				.executes(InstantLore::modsSummary)
				.then(CommandManager.literal("find")
						.then(CommandManager.argument("образец", StringArgumentType.word())
								.executes(ctx -> modsFind(ctx,
										StringArgumentType.getString(ctx, "образец")))))
				.then(CommandManager.argument("ник", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.command.CommandSource
								.suggestMatching(ctx.getSource().getPlayerNames(), builder))
						.executes(ctx -> modsOfPlayer(ctx,
								StringArgumentType.getString(ctx, "ник"))));
	}

	private static int modsSummary(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		var players = source.getServer().getPlayerManager().getPlayerList();

		StringBuilder sb = new StringBuilder("Моды клиентов (доклад недоверенный — только для разговора):");

		if (players.isEmpty()) {
			sb.append("\n  никого нет онлайн");
		}

		int silent = 0;
		for (ServerPlayerEntity player : players) {
			String name = player.getName().getString();
			ClientMods.Report report = ClientMods.reported(player.getUuid());

			if (report == null) {
				silent++;
				sb.append("\n  ? ").append(name)
						.append(" — нет доклада (клиент старше 0.85.0 либо ещё не доложил)");
				continue;
			}

			List<String> risky = ClientMods.risky(report);
			sb.append(risky.isEmpty() ? "\n  · " : "\n  ⚠ ").append(name)
					.append(" — модов: ").append(report.total());

			if (!risky.isEmpty()) {
				sb.append(", сквозь слепоту: ");
				for (int i = 0; i < risky.size(); i++) {
					sb.append(i == 0 ? "" : ", ").append(ClientMods.idOf(risky.get(i)));
				}
			}
			if (report.truncated()) {
				sb.append(" (список срезан лимитом пакета: прислано ")
						.append(report.mods().size()).append(')');
			}
		}

		if (!players.isEmpty() && silent > 0) {
			sb.append("\nБез доклада: ").append(silent).append('/').append(players.size())
					.append(" — им нужен клиент 0.85.0+.");
		}

		feedbackLines(source, sb.toString());
		return players.size() - silent;
	}

	private static int modsOfPlayer(CommandContext<ServerCommandSource> ctx, String nick) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(nick);

		if (player == null) {
			source.sendError(Text.literal("Игрок " + nick
					+ " не онлайн — список модов живёт в сессии и офлайн его взять неоткуда"));
			return 0;
		}

		ClientMods.Report report = ClientMods.reported(player.getUuid());
		if (report == null) {
			source.sendError(Text.literal(nick + " не докладывает список модов — клиент старше "
					+ "0.85.0 (проверить версию: /lore versions)"));
			return 0;
		}

		List<String> risky = ClientMods.risky(report);

		StringBuilder sb = new StringBuilder("Моды ").append(nick).append(" — ")
				.append(report.total()).append(" шт. (доклад с его машины, не проверяемый):");

		if (!risky.isEmpty()) {
			sb.append("\n  Проходят сквозь слепоту лимбо:");
			for (String entry : risky) {
				sb.append("\n    ⚠ ").append(entry)
						.append(" — ").append(ClientMods.riskOf(entry).label());
			}
		} else {
			sb.append("\n  Известных сквозных модов нет — но таблица знает только вписанное в неё.");
		}

		int fabricModules = 0;
		sb.append("\n  Всё остальное:");
		for (String entry : report.mods()) {
			if (ClientMods.riskOf(entry) != null) {
				continue;
			}
			if (ClientMods.isFabricApiModule(entry)) {
				fabricModules++;
				continue;
			}
			sb.append("\n    ").append(entry);
		}
		if (fabricModules > 0) {
			sb.append("\n    (+ модулей fabric-api: ").append(fabricModules).append(')');
		}

		if (report.truncated()) {
			sb.append("\n  ⚠ Список срезан лимитом пакета: прислано ").append(report.mods().size())
					.append(" из ").append(report.total()).append('.');
		}

		feedbackLines(source, sb.toString());
		return risky.size();
	}

	private static int modsFind(CommandContext<ServerCommandSource> ctx, String pattern) {
		ServerCommandSource source = ctx.getSource();
		String needle = pattern.toLowerCase(java.util.Locale.ROOT);

		StringBuilder sb = new StringBuilder("Кто держит мод по образцу '").append(pattern).append("':");
		int found = 0;

		for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
			ClientMods.Report report = ClientMods.reported(player.getUuid());
			if (report == null) {
				continue;
			}

			List<String> hits = new java.util.ArrayList<>();
			for (String entry : report.mods()) {
				if (ClientMods.idOf(entry).contains(needle)) {
					hits.add(entry);
				}
			}

			if (!hits.isEmpty()) {
				found++;
				sb.append("\n  ").append(player.getName().getString()).append(": ")
						.append(String.join(", ", hits));
			}
		}

		if (found == 0) {
			sb.append("\n  ни у кого из доложивших");
		}

		feedbackLines(source, sb.toString());
		return found;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> defineCommand() {
		return CommandManager.literal("define")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(0, LoreContent.POOL_SIZE - 1))
						.then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("hardness", FloatArgumentType.floatArg(0.1F))
										.then(CommandManager.argument("luminance", IntegerArgumentType.integer(0, 15))
												.executes(InstantLore::define)))));
	}

	private static int define(CommandContext<ServerCommandSource> ctx) {
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		String name = StringArgumentType.getString(ctx, "name");
		float hardness = FloatArgumentType.getFloat(ctx, "hardness");
		int luminance = IntegerArgumentType.getInteger(ctx, "luminance");

		LoreContent.defineBlock(slot, new LoreBlockDefinition(
				name, name, hardness, hardness, luminance, BlockSoundGroup.AMETHYST_BLOCK, true,
				false, "", 0.0, "self", "", null, true, false));

		ilReply(ctx.getSource(), () -> Text.literal(
				"Слот " + slot + " → " + name
						+ " (твёрдость " + hardness + ", свет " + luminance + ")"), false);
		return 1;
	}

	private static LiteralArgumentBuilder<ServerCommandSource> inspectCommand() {
		return CommandManager.literal("inspect")
				.then(CommandManager.argument("slot", IntegerArgumentType.integer(0, LoreContent.POOL_SIZE - 1))
						.executes(InstantLore::inspect));
	}

	private static int inspect(CommandContext<ServerCommandSource> ctx) {
		int slot = IntegerArgumentType.getInteger(ctx, "slot");
		ServerCommandSource source = ctx.getSource();
		BlockState state = LoreBlocks.get(slot).currentState();

		boolean gameSeesCollision = !state.getCollisionShape(source.getWorld(),
				BlockPos.ofFloored(source.getPosition())).isEmpty();
		boolean definitionSaysSolid = LoreBlocks.get(slot).definition().solid();

		ilReply(source, () -> Text.literal(
				"Слот " + slot + " глазами игры:"
						+ " свет=" + state.getLuminance()
						+ " звук=" + state.getSoundGroup().getBreakSound().id().getPath()
						+ " коллизия=" + gameSeesCollision
						+ " (в определении solid=" + definitionSaysSolid + ")"), false);
		return 1;
	}
}
