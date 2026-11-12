package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.mixin.server.PlayerListS2CPacketAccessor;
import com.rufana.instantlore.mixin.server.ServerChunkLoadingManagerInvoker;
import com.rufana.instantlore.server.LoreDevLog;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvisibleModule implements LoreModule {

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("invisible.json");

	private static final Map<UUID, String> HIDDEN = new ConcurrentHashMap<>();

	private static volatile boolean active;

	private static final ServerBossBar VANISH_BAR = new ServerBossBar(
			Text.literal("Вы невидимы").formatted(Formatting.LIGHT_PURPLE),
			BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

	private static final ServerBossBar STAFF_BAR = new ServerBossBar(
			Text.literal("Вы невидимы — в том числе для админов").formatted(Formatting.RED),
			BossBar.Color.RED, BossBar.Style.PROGRESS);

	private static volatile boolean fakeJoinLeave = true;

	private static volatile boolean disableChat = false;

	private static volatile boolean invulnerable = true;

	private static volatile boolean hideDeath = true;

	private static volatile boolean hideAdvancements = true;

	private static volatile boolean disableMsg = false;

	private static volatile boolean hideFromSleep = true;

	private static volatile boolean hideFromMobs = true;

	private static volatile boolean suppressVibrations = true;

	private static volatile boolean hideFromSpawning = true;

	private static volatile boolean hideFromPing = true;

	private static volatile boolean hideFromMaps = true;

	private static volatile boolean hideFromBlocks = true;

	private static volatile boolean hideCollisions = true;

	private static volatile boolean hideChestOpen = true;

	private static volatile boolean hidePickup = true;

	private static final java.util.Set<UUID> PICKUP_ALLOWED = ConcurrentHashMap.newKeySet();

	private static volatile boolean hideSounds = true;

	private static final java.util.Set<UUID> STAFF_HIDDEN = ConcurrentHashMap.newKeySet();

	private static final Map<UUID, String> ALLOWED = new ConcurrentHashMap<>();

	private static volatile MinecraftServer SERVER;

	private static final String VANISH_TAG = "instantlore_vanished";

	private static final String STAFF_VANISH_TAG = "instantlore_staff_hidden";

	public static boolean chatDisabledFor(ServerPlayerEntity player) {
		return active && disableChat && HIDDEN.containsKey(player.getUuid());
	}

	public static boolean commandMessageDisabledFor(ServerPlayerEntity player) {
		return player != null && active && disableMsg && HIDDEN.containsKey(player.getUuid());
	}

	public static boolean invulnerableFor(ServerPlayerEntity player) {
		return active && invulnerable && HIDDEN.containsKey(player.getUuid());
	}

	public static boolean hiddenFromSleep(ServerPlayerEntity player) {
		return active && hideFromSleep && HIDDEN.containsKey(player.getUuid());
	}


	private static boolean isHiddenEntity(net.minecraft.entity.Entity e) {
		return e instanceof ServerPlayerEntity p && HIDDEN.containsKey(p.getUuid());
	}

	public static boolean untargetable(net.minecraft.entity.LivingEntity target) {
		return active && hideFromMobs && isHiddenEntity(target);
	}

	public static boolean vibrationSuppressed(net.minecraft.entity.Entity source) {
		return active && suppressVibrations && isHiddenEntity(source);
	}

	public static boolean soundSuppressed(net.minecraft.entity.Entity source) {
		return active && hideSounds && isHiddenEntity(source);
	}

	public static boolean skippedBySpawning(ServerPlayerEntity player) {
		return active && hideFromSpawning && HIDDEN.containsKey(player.getUuid());
	}

	public static boolean hiddenFromPing(ServerPlayerEntity player) {
		return active && hideFromPing && HIDDEN.containsKey(player.getUuid());
	}

	public static boolean hiddenFromMaps(net.minecraft.entity.player.PlayerEntity player) {
		return active && hideFromMaps && player instanceof ServerPlayerEntity p
				&& HIDDEN.containsKey(p.getUuid());
	}

	public static List<ServerPlayerEntity> filterVisible(List<ServerPlayerEntity> in) {
		if (!active || !hideFromPing || HIDDEN.isEmpty() || in.isEmpty()) {
			return in;
		}
		List<ServerPlayerEntity> out = new ArrayList<>(in.size());
		for (ServerPlayerEntity p : in) {
			if (!HIDDEN.containsKey(p.getUuid())) {
				out.add(p);
			}
		}
		return out.size() == in.size() ? in : out;
	}


	public static <T extends net.minecraft.entity.Entity> List<T> filterBlockEntities(List<T> in) {
		if (!active || !hideFromBlocks || HIDDEN.isEmpty() || in.isEmpty()) {
			return in;
		}
		List<T> out = new ArrayList<>(in.size());
		for (T e : in) {
			if (!isHiddenEntity(e)) {
				out.add(e);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static List<? extends Entity> filterPresenceEntities(List<? extends Entity> in) {
		if (!active || !hideFromBlocks || HIDDEN.isEmpty() || in.isEmpty()) {
			return in;
		}
		List<Entity> out = new ArrayList<>(in.size());
		for (Entity e : in) {
			if (!isHiddenEntity(e)) {
				out.add(e);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static boolean notPushable(net.minecraft.entity.LivingEntity entity) {
		return active && hideCollisions && isHiddenEntity(entity);
	}

	public static boolean chestUserHidden(net.minecraft.entity.LivingEntity entity) {
		return active && hideChestOpen && isHiddenEntity(entity);
	}

	public static <T extends net.minecraft.entity.Entity> List<T> filterChestViewers(List<T> in) {
		if (!active || !hideChestOpen || HIDDEN.isEmpty() || in.isEmpty()) {
			return in;
		}
		List<T> out = new ArrayList<>(in.size());
		for (T e : in) {
			if (!isHiddenEntity(e)) {
				out.add(e);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static boolean pickupHidden(net.minecraft.entity.player.PlayerEntity player) {
		return active && hidePickup && isHiddenEntity(player)
				&& !PICKUP_ALLOWED.contains(player.getUuid());
	}

	public static boolean allowPickup(ServerPlayerEntity player, boolean allowed) {
		if (allowed) {
			PICKUP_ALLOWED.add(player.getUuid());
		} else {
			PICKUP_ALLOWED.remove(player.getUuid());
		}
		LoreDevLog.info("Item pickup for hidden {}: {}",
				player.getName().getString(), allowed ? "allowed" : "closed");
		return allowed;
	}

	public static boolean pickupAllowed(ServerPlayerEntity player) {
		return PICKUP_ALLOWED.contains(player.getUuid());
	}

	public static boolean blockStepHidden(net.minecraft.entity.Entity entity) {
		return active && hideFromBlocks && isHiddenEntity(entity);
	}

	public static List<ServerPlayerEntity> filterForSleep(List<ServerPlayerEntity> in) {
		if (!active || !hideFromSleep || HIDDEN.isEmpty() || in.isEmpty()) {
			return in;
		}
		List<ServerPlayerEntity> out = new ArrayList<>(in.size());
		for (ServerPlayerEntity p : in) {
			if (!HIDDEN.containsKey(p.getUuid())) {
				out.add(p);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static List<ServerPlayerEntity> filterSelectorPlayers(
			ServerCommandSource source, List<ServerPlayerEntity> in) {
		if (!hasHidden() || in.isEmpty() || source.getPlayer() == null) {
			return in;
		}
		ServerPlayerEntity viewer = source.getPlayer();
		List<ServerPlayerEntity> out = new ArrayList<>(in.size());
		for (ServerPlayerEntity p : in) {
			if (!concealedFrom(viewer, p.getUuid())) {
				out.add(p);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static List<ServerPlayerEntity> filterListPlayers(
			ServerCommandSource source, List<ServerPlayerEntity> in) {
		if (!hasHidden() || in.isEmpty()) {
			return in;
		}
		ServerPlayerEntity viewer = source.getPlayer();
		List<ServerPlayerEntity> out = new ArrayList<>(in.size());
		for (ServerPlayerEntity p : in) {
			boolean concealed = viewer == null ? staffHidden(p.getUuid()) : concealedFrom(viewer, p.getUuid());
			if (!concealed) {
				out.add(p);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static List<? extends Entity> filterSelectorEntities(
			ServerCommandSource source, List<? extends Entity> in) {
		if (!hasHidden() || in.isEmpty() || source.getPlayer() == null) {
			return in;
		}
		ServerPlayerEntity viewer = source.getPlayer();
		List<Entity> out = new ArrayList<>(in.size());
		for (Entity e : in) {
			if (!(e instanceof ServerPlayerEntity p) || !concealedFrom(viewer, p.getUuid())) {
				out.add(e);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	public static java.util.Collection<String> filterPlayerNames(
			ServerCommandSource source, java.util.Collection<String> in) {
		if (!hasHidden() || in.isEmpty()) {
			return in;
		}
		ServerPlayerEntity viewer = source.getPlayer();
		if (viewer == null) {
			List<String> out = new ArrayList<>(in.size());
			for (String name : in) {
				if (!staffHiddenName(name)) {
					out.add(name);
				}
			}
			return out.size() == in.size() ? in : out;
		}
		String selfName = viewer.getName().getString();
		List<String> out = new ArrayList<>(in.size());
		for (String name : in) {
			if (!concealedNameFrom(viewer, name, selfName)) {
				out.add(name);
			}
		}
		return out.size() == in.size() ? in : out;
	}

	private static boolean concealedFrom(ServerPlayerEntity viewer, UUID target) {
		if (!HIDDEN.containsKey(target)) {
			return false;
		}
		if (viewer != null && viewer.getUuid().equals(target)) {
			return false;
		}
		if (staffHidden(target)) {
			return true;
		}
		return !seesVanishedTarget(viewer, target);
	}

	private static boolean concealedNameFrom(ServerPlayerEntity viewer, String name, String selfName) {
		if (!isHiddenName(name)) {
			return false;
		}
		if (name.equalsIgnoreCase(selfName)) {
			return false;
		}
		if (staffHiddenName(name)) {
			return true;
		}
		return !seesVanishedTargetName(viewer, name);
	}

	public static boolean concealedFromViewer(ServerPlayerEntity viewer, UUID target) {
		return active && target != null && concealedFrom(viewer, target);
	}

	public static boolean seesVanished(ServerPlayerEntity player) {
		return player != null && Permissions.check(player, "vanish.general.vanished");
	}

	private static String targetNode(String account) {
		return "vanish.general.vanished." + account.toLowerCase(java.util.Locale.ROOT);
	}

	public static boolean seesVanishedTarget(ServerPlayerEntity viewer, UUID target) {
		if (seesVanished(viewer)) {
			return true;
		}
		if (viewer == null) {
			return false;
		}
		String account = HIDDEN.get(target);
		return account != null && Permissions.check(viewer, targetNode(account));
	}

	private static boolean seesVanishedTarget(ServerPlayerEntity viewer, ServerPlayerEntity target) {
		if (seesVanished(viewer)) {
			return true;
		}
		if (viewer == null || target == null) {
			return false;
		}
		return Permissions.check(viewer, targetNode(target.getName().getString()));
	}

	private static boolean seesVanishedTargetName(ServerPlayerEntity viewer, String shown) {
		if (seesVanished(viewer)) {
			return true;
		}
		if (viewer == null) {
			return false;
		}
		UUID target = uuidOfHiddenName(shown);
		if (target == null) {
			return false;
		}
		String account = HIDDEN.get(target);
		return account != null && Permissions.check(viewer, targetNode(account));
	}

	public static boolean apiConcealedFrom(ServerPlayerEntity viewer, ServerPlayerEntity target) {
		if (!active || target == null) {
			return false;
		}
		return concealedFrom(viewer, target.getUuid());
	}

	public static boolean tabCanSee(ServerPlayerEntity viewer, UUID target) {
		return !active || !concealedFrom(viewer, target);
	}

	@Override
	public String id() {
		return "invisible";
	}

	@Override
	public String title() {
		return "Невидимка (скрытие присутствия)";
	}

	@Override
	public boolean defaultEnabled() {
		return true;
	}

	@Override
	public void onEnable(MinecraftServer server) {
		active = true;
	}

	@Override
	public void onDisable(MinecraftServer server) {
		if (server != null && !server.isRunning()) {
			return;
		}
		active = false;
	}

	@Override
	public String describe() {
		return "в списке: " + HIDDEN.size() + ", из них скрыты и от админов: " + STAFF_HIDDEN.size()
				+ ", допущено: " + ALLOWED.size()
				+ (active ? "" : " (модуль выкл — не применяется)");
	}


	public static boolean isHidden(UUID uuid) {
		return active && HIDDEN.containsKey(uuid);
	}

	public static boolean hasHidden() {
		return active && !HIDDEN.isEmpty();
	}

	public static UUID hiddenBroadcastSubject(Text message) {
		if (!active || message == null) {
			return null;
		}
		if (message.getContent() instanceof TranslatableTextContent content) {
			String key = content.getKey();
			boolean death = hideDeath && key.startsWith("death.");
			boolean candidate =
					key.startsWith("multiplayer.player.joined") || key.startsWith("multiplayer.player.left")
					|| death
					|| (hideAdvancements && key.startsWith("chat.type.advancement."));
			if (candidate) {
				Object[] args = content.getArgs();
				int limit = Math.min(1, args.length);
				for (int i = 0; i < limit; i++) {
					if (args[i] instanceof Text name) {
						UUID found = uuidOfHiddenName(name.getString());
						if (found != null) {
							return found;
						}
					}
				}
			}
		}
		return null;
	}

	private static void relayToViewers(UUID subject, Text message) {
		MinecraftServer server = server();
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getUuid().equals(subject) || concealedFrom(viewer, subject)) {
				continue;
			}
			viewer.sendMessage(message);
		}
		server.sendMessage(message);
	}

	public static void relayHiddenBroadcast(UUID subject, Text message) {
		try {
			relayToViewers(subject, message);
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[invisible] targeted system message broadcast failed", t);
		}
	}
	public static Text deathHiddenKillerScrub(Text message) {
		if (!active || !hideDeath || message == null) {
			return null;
		}
		if (!(message.getContent() instanceof TranslatableTextContent content)
				|| !content.getKey().startsWith("death.")) {
			return null;
		}
		Object[] args = content.getArgs();
		if (args.length == 0 || !(args[0] instanceof Text victim)) {
			return null;
		}
		if (uuidOfHiddenName(victim.getString()) != null) {
			return null;
		}
		boolean hiddenElsewhere = false;
		for (int i = 1; i < args.length; i++) {
			if (args[i] instanceof Text t && uuidOfHiddenName(t.getString()) != null) {
				hiddenElsewhere = true;
				break;
			}
		}
		if (!hiddenElsewhere) {
			return null;
		}
		return Text.translatable("death.attack.generic", victim);
	}

	public static void broadcastPublic(Text message) {
		MinecraftServer server = server();
		if (server == null) {
			return;
		}
		try {
			for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
				viewer.sendMessage(message);
			}
			server.sendMessage(message);
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[invisible] public broadcast of anonymized death failed", t);
		}
	}

	private static boolean isHiddenName(String shown) {
		return uuidOfHiddenName(shown) != null;
	}

	private static UUID uuidOfHiddenName(String shown) {
		if (shown == null || shown.isEmpty() || HIDDEN.isEmpty()) {
			return null;
		}
		String lowerShown = shown.toLowerCase(java.util.Locale.ROOT);
		for (Map.Entry<UUID, String> entry : HIDDEN.entrySet()) {
			String account = entry.getValue();
			if (account != null) {
				if (account.equalsIgnoreCase(shown)
						|| containsWord(lowerShown, account.toLowerCase(java.util.Locale.ROOT))) {
					return entry.getKey();
				}
			}
			ServerPlayerEntity player = playerOf(entry.getKey());
			if (player != null) {
				if (player.getName().getString().equalsIgnoreCase(shown)) {
					return entry.getKey();
				}
				Text display = player.getDisplayName();
				if (display != null && display.getString().equals(shown)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	public static boolean mentionsHidden(String text) {
		if (!active || text == null || text.isEmpty() || HIDDEN.isEmpty()) {
			return false;
		}
		String lower = text.toLowerCase(java.util.Locale.ROOT);
		for (var entry : HIDDEN.entrySet()) {
			if (!logFilterOwner(entry.getValue()) || !STAFF_HIDDEN.contains(entry.getKey())) {
				continue;
			}
			if (containsWord(lower, entry.getValue().toLowerCase(java.util.Locale.ROOT))) {
				return true;
			}
			String uuid = entry.getKey().toString().toLowerCase(java.util.Locale.ROOT);
			if (lower.contains(uuid) || lower.contains(uuid.replace("-", ""))) {
				return true;
			}
		}
		return false;
	}

	private static boolean logFilterOwner(String name) {
		return name != null && name.equalsIgnoreCase(InstantLore.TECH_ADMIN);
	}

	public static boolean staffHidden(UUID uuid) {
		return active && HIDDEN.containsKey(uuid) && STAFF_HIDDEN.contains(uuid);
	}

	private static boolean staffHiddenName(String name) {
		if (name == null || STAFF_HIDDEN.isEmpty()) {
			return false;
		}
		for (UUID uuid : STAFF_HIDDEN) {
			String account = HIDDEN.get(uuid);
			if (account != null && account.equalsIgnoreCase(name)) {
				return true;
			}
			ServerPlayerEntity player = playerOf(uuid);
			if (player != null) {
				Text display = player.getDisplayName();
				if (player.getName().getString().equalsIgnoreCase(name)
						|| (display != null && display.getString().equals(name))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean staffHidden(ServerPlayerEntity player) {
		return player != null && staffHidden(player.getUuid());
	}

	private static boolean containsWord(String haystack, String needle) {
		if (needle.isEmpty()) {
			return false;
		}
		int from = 0;
		while (true) {
			int i = haystack.indexOf(needle, from);
			if (i < 0) {
				return false;
			}
			boolean leftOk = i == 0 || !isNameChar(haystack.charAt(i - 1));
			int end = i + needle.length();
			boolean rightOk = end >= haystack.length() || !isNameChar(haystack.charAt(end));
			if (leftOk && rightOk) {
				return true;
			}
			from = i + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	public static net.minecraft.network.packet.Packet<?> filterPlayerList(
			net.minecraft.network.packet.Packet<?> packet, ServerPlayerEntity receiver) {
		if (!active || HIDDEN.isEmpty() || receiver == null
				|| !(packet instanceof PlayerListS2CPacket list)
				|| !list.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
			return packet;
		}

		boolean needsFilter = false;
		for (var entry : list.getEntries()) {
			if (concealedFrom(receiver, entry.profileId())) {
				needsFilter = true;
				break;
			}
		}
		if (!needsFilter) {
			return packet;
		}

		List<PlayerListS2CPacket.Entry> rewritten = new ArrayList<>(list.getEntries().size());
		for (var entry : list.getEntries()) {
			if (concealedFrom(receiver, entry.profileId())) {
				rewritten.add(new PlayerListS2CPacket.Entry(entry.profileId(), entry.profile(),
						false, entry.latency(), entry.gameMode(), entry.displayName(),
						entry.showHat(), entry.listOrder(), entry.chatSession()));
			} else {
				rewritten.add(entry);
			}
		}
		PlayerListS2CPacket out = new PlayerListS2CPacket(list.getActions(), List.of());
		((PlayerListS2CPacketAccessor) (Object) out).instantlore$setEntries(rewritten);
		return out;
	}


	public static boolean isVanished(ServerPlayerEntity player) {
		return HIDDEN.containsKey(player.getUuid());
	}

	public static void setVanished(ServerPlayerEntity player, boolean vanished) {
		MinecraftServer server = player.getEntityWorld().getServer();
		if (server == null) {
			return;
		}

		if (vanished) {
			if (HIDDEN.containsKey(player.getUuid())) {
				return;
			}
			HIDDEN.put(player.getUuid(), player.getName().getString());
			save();
			sendFakeJoinLeave(server, player, false);
			syncListedToOthers(server, player);
			refreshTracking(player);
			syncVanishTag(player);
			updateBar(player);
			fireVanishEvent(player, true);
			LoreDevLog.info("Invisibility on: {}", player.getName().getString());
		} else {
			List<ServerPlayerEntity> fakeAudience = fakeAudience(server, player);
			if (HIDDEN.remove(player.getUuid()) == null) {
				return;
			}
			PICKUP_ALLOWED.remove(player.getUuid());
			save();
			server.getPlayerManager().sendToAll(
					new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, player));
			syncListedToOthers(server, player);
			refreshTracking(player);
			syncVanishTag(player);
			updateBar(player);
			deliverFake(server, fakeAudience, Text.translatable(
					"multiplayer.player.joined", player.getDisplayName()).formatted(Formatting.YELLOW));
			fireVanishEvent(player, false);
			LoreDevLog.info("Invisibility off: {}", player.getName().getString());
		}
	}

	private static List<ServerPlayerEntity> fakeAudience(MinecraftServer server, ServerPlayerEntity player) {
		List<ServerPlayerEntity> out = new ArrayList<>();
		out.add(player);
		boolean fromStaffToo = STAFF_HIDDEN.contains(player.getUuid());
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getUuid().equals(player.getUuid())) {
				continue;
			}
			if (fromStaffToo || !seesVanishedTarget(viewer, player)) {
				out.add(viewer);
			}
		}
		return out;
	}

	private static void sendFakeJoinLeave(MinecraftServer server, ServerPlayerEntity player, boolean joined) {
		if (!fakeJoinLeave) {
			return;
		}
		deliverFake(server, fakeAudience(server, player), Text.translatable(
				joined ? "multiplayer.player.joined" : "multiplayer.player.left",
				player.getDisplayName()).formatted(Formatting.YELLOW));
	}

	private static void tellStaffAudience(MinecraftServer server, ServerPlayerEntity player, boolean on) {
		if (!fakeJoinLeave) {
			return;
		}
		Text text = Text.translatable(
				on ? "multiplayer.player.left" : "multiplayer.player.joined",
				player.getDisplayName()).formatted(Formatting.YELLOW);
		player.sendMessage(text);
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getUuid().equals(player.getUuid()) || !seesVanishedTarget(viewer, player)) {
				continue;
			}
			viewer.sendMessage(text);
		}
	}

	private static void deliverFake(MinecraftServer server, List<ServerPlayerEntity> audience, Text text) {
		if (!fakeJoinLeave) {
			return;
		}
		for (ServerPlayerEntity viewer : audience) {
			viewer.sendMessage(text);
		}
		server.sendMessage(text);
	}

	private static void updateBar(ServerPlayerEntity player) {
		boolean hidden = HIDDEN.containsKey(player.getUuid());
		boolean fromStaff = hidden && STAFF_HIDDEN.contains(player.getUuid());
		if (fromStaff) {
			VANISH_BAR.removePlayer(player);
			STAFF_BAR.addPlayer(player);
		} else if (hidden) {
			STAFF_BAR.removePlayer(player);
			VANISH_BAR.addPlayer(player);
		} else {
			VANISH_BAR.removePlayer(player);
			STAFF_BAR.removePlayer(player);
		}
	}

	public static boolean setStaffHidden(ServerPlayerEntity player, boolean on) {
		UUID uuid = player.getUuid();
		boolean changed = on ? STAFF_HIDDEN.add(uuid) : STAFF_HIDDEN.remove(uuid);
		if (!changed) {
			return on;
		}
		save();
		syncVanishTag(player);
		if (HIDDEN.containsKey(uuid)) {
			MinecraftServer server = server();
			if (server != null) {
				server.getPlayerManager().sendToAll(
						new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, player));
				syncListedToOthers(server, player);
				refreshTracking(player);
				tellStaffAudience(server, player, on);
			}
			updateBar(player);
			fireVanishEvent(player, true);
		}
		LoreDevLog.info("Hiding from admins for {}: {}", player.getName().getString(), on ? "on" : "off");
		return on;
	}

	public static boolean staffHideRequested(ServerPlayerEntity player) {
		return player != null && STAFF_HIDDEN.contains(player.getUuid());
	}


	public static boolean accessAllowed(ServerPlayerEntity player) {
		return player != null && ALLOWED.containsKey(player.getUuid());
	}

	public static boolean grantAccess(UUID uuid, String name) {
		if (ALLOWED.containsKey(uuid)) {
			return false;
		}
		ALLOWED.put(uuid, name);
		save();
		LoreDevLog.info("Invisibility access granted: {}", name);
		return true;
	}

	public static String revokeAccess(String name) {
		UUID found = null;
		for (Map.Entry<UUID, String> entry : ALLOWED.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				found = entry.getKey();
				break;
			}
		}
		if (found == null) {
			return null;
		}
		String removed = ALLOWED.remove(found);
		ServerPlayerEntity player = playerOf(found);
		if (player != null) {
			setStaffHidden(player, false);
		} else {
			STAFF_HIDDEN.remove(found);
		}
		save();
		LoreDevLog.info("Access to the 'and from admins' toggle revoked: {}", removed);
		return removed;
	}

	public static List<String> accessList() {
		List<String> out = new ArrayList<>(ALLOWED.size());
		for (Map.Entry<UUID, String> entry : ALLOWED.entrySet()) {
			out.add(entry.getValue()
					+ (STAFF_HIDDEN.contains(entry.getKey()) ? " (тумблер «от админов» вкл)" : ""));
		}
		return out;
	}

	private static void fireVanishEvent(ServerPlayerEntity player, boolean vanished) {
		try {
			me.drex.vanish.api.VanishEvents.VANISH_EVENT.invoker().onVanish(player, vanished);
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[vanish-api] vanish event broadcast failed", t);
		}
	}

	private static void syncVanishTag(ServerPlayerEntity player) {
		if (isHidden(player.getUuid())) {
			player.addCommandTag(VANISH_TAG);
		} else {
			player.removeCommandTag(VANISH_TAG);
		}
		if (staffHidden(player.getUuid())) {
			player.addCommandTag(STAFF_VANISH_TAG);
		} else {
			player.removeCommandTag(STAFF_VANISH_TAG);
		}
	}

	private static void syncListedToOthers(MinecraftServer server, ServerPlayerEntity player) {
		PlayerListS2CPacket hiddenPacket = listedPacket(player, false);
		PlayerListS2CPacket shownPacket = listedPacket(player, true);
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other.getUuid().equals(player.getUuid())) {
				continue;
			}
			other.networkHandler.sendPacket(
					concealedFrom(other, player.getUuid()) ? hiddenPacket : shownPacket);
		}
	}

	private static PlayerListS2CPacket listedPacket(ServerPlayerEntity player, boolean listed) {
		PlayerListS2CPacket packet = new PlayerListS2CPacket(
				EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LISTED), List.of(player));
		PlayerListS2CPacket.Entry entry = new PlayerListS2CPacket.Entry(
				player.getUuid(), player.getGameProfile(), listed, 0,
				player.interactionManager.getGameMode(), null, false, 0, null);
		((PlayerListS2CPacketAccessor) (Object) packet).instantlore$setEntries(List.of(entry));
		return packet;
	}

	private static void refreshTracking(ServerPlayerEntity player) {
		if (player.getEntityWorld() instanceof ServerWorld world) {
			ServerChunkLoadingManagerInvoker mgr =
					(ServerChunkLoadingManagerInvoker) world.getChunkManager().chunkLoadingManager;
			mgr.instantlore$unloadEntity(player);
			mgr.instantlore$loadEntity(player);
		}
	}


	public static boolean add(ServerPlayerEntity player) {
		if (isVanished(player)) {
			return false;
		}
		setVanished(player, true);
		return true;
	}

	public static boolean addOffline(UUID uuid, String name) {
		if (HIDDEN.containsKey(uuid)) {
			return false;
		}
		HIDDEN.put(uuid, name);
		save();
		LoreDevLog.info("Invisible player added (offline): {}", name);
		return true;
	}

	public static String remove(String name) {
		UUID found = null;
		for (Map.Entry<UUID, String> entry : HIDDEN.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) {
				found = entry.getKey();
				break;
			}
		}
		if (found == null) {
			return null;
		}
		String removed = HIDDEN.get(found);
		ServerPlayerEntity player = playerOf(found);
		if (player != null) {
			setVanished(player, false);
		} else {
			HIDDEN.remove(found);
			save();
			LoreDevLog.info("Invisible player removed from list (offline): {}", removed);
		}
		return removed;
	}

	private static ServerPlayerEntity playerOf(UUID uuid) {
		MinecraftServer server = server();
		return server == null ? null : server.getPlayerManager().getPlayer(uuid);
	}

	private static MinecraftServer server() {
		return SERVER;
	}

	public static List<String> list() {
		return new ArrayList<>(HIDDEN.values());
	}


	public static void registerStorage() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SERVER = server;
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SERVER = null);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			syncVanishTag(handler.player);
			updateBar(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			VANISH_BAR.removePlayer(handler.player);
			STAFF_BAR.removePlayer(handler.player);
			PICKUP_ALLOWED.remove(handler.player.getUuid());
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (chatDisabledFor(sender)) {
				sender.sendMessage(Text.literal("You cannot chat while vanished."), false);
				return false;
			}
			return true;
		});

		ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
			ServerPlayerEntity sender = source.getPlayer();
			if (commandMessageDisabledFor(sender)) {
				sender.sendMessage(Text.literal("You cannot send messages while vanished."), false);
				return false;
			}
			return true;
		});
	}

	private static void load() {
		HIDDEN.clear();
		ALLOWED.clear();
		STAFF_HIDDEN.clear();
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			String json = Files.readString(STORAGE, StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(json);
			if (parsed.isJsonArray()) {
				readHidden(parsed.getAsJsonArray());
				InstantLore.LOGGER.info(
						"Invisible players loaded: {} (old format - will be upgraded on first write)", HIDDEN.size());
				return;
			}
			if (!parsed.isJsonObject()) {
				InstantLore.LOGGER.error("invisible.json - expected an array or object, found: {}", parsed);
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			if (root.has("hidden") && root.get("hidden").isJsonArray()) {
				readHidden(root.getAsJsonArray("hidden"));
			}
			if (root.has("allowed") && root.get("allowed").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("allowed")) {
					JsonObject entry = element.getAsJsonObject();
					ALLOWED.put(UUID.fromString(entry.get("uuid").getAsString()),
							entry.get("name").getAsString());
				}
			}
			if (root.has("staff_hidden") && root.get("staff_hidden").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("staff_hidden")) {
					STAFF_HIDDEN.add(UUID.fromString(element.getAsJsonObject()
							.get("uuid").getAsString()));
				}
			}
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read invisible.json: {}", e.toString());
		}
	}

	private static void readHidden(JsonArray array) {
		for (JsonElement element : array) {
			JsonObject entry = element.getAsJsonObject();
			UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
			HIDDEN.put(uuid, entry.get("name").getAsString());
		}
	}

	private static void save() {
		try {
			Files.createDirectories(STORAGE.getParent());
			JsonArray hidden = new JsonArray();
			for (Map.Entry<UUID, String> entry : HIDDEN.entrySet()) {
				JsonObject object = new JsonObject();
				object.addProperty("uuid", entry.getKey().toString());
				object.addProperty("name", entry.getValue());
				hidden.add(object);
			}
			JsonArray staffHidden = new JsonArray();
			for (UUID uuid : STAFF_HIDDEN) {
				JsonObject object = new JsonObject();
				object.addProperty("uuid", uuid.toString());
				String name = HIDDEN.get(uuid);
				object.addProperty("name", name != null ? name : ALLOWED.getOrDefault(uuid, "?"));
				staffHidden.add(object);
			}
			JsonArray allowed = new JsonArray();
			for (Map.Entry<UUID, String> entry : ALLOWED.entrySet()) {
				JsonObject object = new JsonObject();
				object.addProperty("uuid", entry.getKey().toString());
				object.addProperty("name", entry.getValue());
				allowed.add(object);
			}
			JsonObject root = new JsonObject();
			root.add("hidden", hidden);
			root.add("allowed", allowed);
			root.add("staff_hidden", staffHidden);
			Path temp = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
			Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
			Files.move(temp, STORAGE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to save invisible.json: {}", e.toString());
		}
	}

	@Override
	public void configure(JsonObject config) {
		fakeJoinLeave = readBool(config, "fake_join_leave", fakeJoinLeave);
		disableChat = readBool(config, "disable_chat", disableChat);
		invulnerable = readBool(config, "invulnerable", invulnerable);
		hideDeath = readBool(config, "hide_death", hideDeath);
		hideAdvancements = readBool(config, "hide_advancements", hideAdvancements);
		disableMsg = readBool(config, "disable_msg", disableMsg);
		hideFromSleep = readBool(config, "hide_from_sleep", hideFromSleep);
		hideFromMobs = readBool(config, "hide_from_mobs", hideFromMobs);
		suppressVibrations = readBool(config, "suppress_vibrations", suppressVibrations);
		hideFromSpawning = readBool(config, "hide_from_spawning", hideFromSpawning);
		hideFromPing = readBool(config, "hide_from_ping", hideFromPing);
		hideFromMaps = readBool(config, "hide_from_maps", hideFromMaps);
		hideFromBlocks = readBool(config, "hide_from_blocks", hideFromBlocks);
		hideCollisions = readBool(config, "hide_collisions", hideCollisions);
		hideChestOpen = readBool(config, "hide_chest_open", hideChestOpen);
		hidePickup = readBool(config, "hide_pickup", hidePickup);
		hideSounds = readBool(config, "hide_sounds", hideSounds);
	}

	private static boolean readBool(JsonObject config, String key, boolean fallback) {
		return config.has(key) && config.get(key).isJsonPrimitive()
				? config.get(key).getAsBoolean() : fallback;
	}
}
