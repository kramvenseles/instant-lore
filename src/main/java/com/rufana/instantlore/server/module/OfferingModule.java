package com.rufana.instantlore.server.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LoreBlockBus;
import com.rufana.instantlore.server.LoreDevLog;
import com.rufana.instantlore.server.PlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OfferingModule implements LoreModule {

	private static final Path STORAGE = FabricLoader.getInstance().getGameDir()
			.resolve("instantlore").resolve("offering.json");

	private record Place(String dimension, BlockPos pos) {
		String id() {
			return dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
		}
	}

	private static final class Altar {
		final Map<String, Integer> need = new LinkedHashMap<>();
		final Map<String, Integer> have = new LinkedHashMap<>();

		final Map<String, String> face = new LinkedHashMap<>();
		String title = "";
		String event = "";
		boolean announced;

		final List<String> lines = new ArrayList<>();

		boolean progress = true;

		final Map<UUID, List<String>> shown = new ConcurrentHashMap<>();

		final List<String> displayed = new ArrayList<>();

		final Map<UUID, Map<String, Integer>> shares = new ConcurrentHashMap<>();

		final Map<UUID, String> names = new ConcurrentHashMap<>();

		long orphanedAt;

		boolean complete() {
			return need.entrySet().stream()
					.allMatch(e -> have.getOrDefault(e.getKey(), 0) >= e.getValue());
		}

		int missing(String spec) {
			return Math.max(0, need.getOrDefault(spec, 0) - have.getOrDefault(spec, 0));
		}

		String specFor(ItemStack stack) {
			for (String spec : need.keySet()) {
				if (matches(spec, stack)) {
					return spec;
				}
			}
			return null;
		}
	}

	private final Map<String, Altar> altars = new ConcurrentHashMap<>();
	private final Map<String, Place> places = new ConcurrentHashMap<>();

	private int radius = 10;
	private int maxAltars = 256;
	private int maxNeed = 9999;
	private static final int MAX_LINES = 8;
	private String perClick = "all";
	private int orphanGraceMinutes = 1440;
	private final Map<String, JsonObject> presets = new LinkedHashMap<>();

	private int sinceTick;

	@Override
	public String id() {
		return "offering";
	}

	@Override
	public String title() {
		return "Жертвенники";
	}

	@Override
	public void configure(JsonObject config) {
		radius = clamp(readInt(config, "radius", 10), 1, 64);
		maxAltars = clamp(readInt(config, "max_altars", 256), 1, 4096);
		maxNeed = clamp(readInt(config, "max_need", 9999), 1, 1_000_000);
		perClick = config.has("per_click") && "one".equals(readString(config, "per_click")) ? "one" : "all";
		orphanGraceMinutes = clamp(readInt(config, "orphan_grace_minutes", 1440), 0, 43200);

		presets.clear();
		if (config.has("presets") && config.get("presets").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : config.getAsJsonObject("presets").entrySet()) {
				if (e.getValue().isJsonObject()) {
					presets.put(e.getKey(), e.getValue().getAsJsonObject());
				}
			}
		}
	}

	@Override
	public String describe() {
		long orphans = altars.values().stream().filter(a -> a.orphanedAt != 0).count();
		return "радиус надписи " + radius + ", за клик " + perClick
				+ ", жертвенников " + altars.size() + "/" + maxAltars
				+ (orphans == 0 ? "" : " (без блока: " + orphans + ")")
				+ (presets.isEmpty() ? "" : ", пресетов " + presets.size());
	}

	@Override
	public void onEnable(MinecraftServer server) {
		load();
	}

	@Override
	public void onDisable(MinecraftServer server) {
		for (Map.Entry<String, Altar> entry : altars.entrySet()) {
			Place place = places.get(entry.getKey());
			if (place != null) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					hide(player, place);
				}
			}
			entry.getValue().shown.clear();
		}
	}


	public void subscribe() {
		LoreBlockBus.subscribeUse((player, hand, stack, world, pos, slot, definition, side, hit, sneaking) ->
				accept(player, hand, stack, world, pos, sneaking));
	}

	private boolean accept(ServerPlayerEntity player, Hand hand, ItemStack stack, ServerWorld world,
			BlockPos pos, boolean sneaking) {
		if (hand == Hand.OFF_HAND && !player.getMainHandStack().isEmpty()) {
			return false;
		}
		if (!ModuleRegistry.isActive(id())) {
			return false;
		}

		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return false;
		}
		if (altar.orphanedAt != 0) {
			revive(place, altar, "щелчок по вернувшемуся блоку");
		}

		if (stack.isEmpty()) {
			return true;
		}

		String item = Registries.ITEM.getId(stack.getItem()).toString();
		String spec = altar.specFor(stack);
		if (spec == null) {
			world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.7f, 0.6f);
			return true;
		}
		int wanted = altar.missing(spec);
		if (wanted == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.5f, 1.4f);
			return true;
		}

		int give = Math.min("one".equals(perClick) ? 1 : stack.getCount(), wanted);
		stack.decrement(give);
		altar.have.merge(spec, give, Integer::sum);
		altar.face.put(spec, item);
		altar.shares.computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>())
				.merge(item, give, Integer::sum);
		altar.names.put(player.getUuid(), player.getName().getString());

		int now = altar.have.getOrDefault(spec, 0);
		int total = altar.need.getOrDefault(spec, 0);
		float pitch = 0.8f + 0.6f * (total == 0 ? 1f : (float) now / total);
		world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, pitch);

		if (altar.complete() && !altar.announced) {
			altar.announced = true;
			announce(world, place, altar, player);
		}

		save();
		refresh(world, place, altar);
		return true;
	}

	public String fill(ServerWorld world, BlockPos pos, ServerPlayerEntity by) {
		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return "здесь жертвенник не настроен";
		}
		for (Map.Entry<String, Integer> entry : altar.need.entrySet()) {
			altar.have.put(entry.getKey(), entry.getValue());
		}
		if (altar.complete() && !altar.announced) {
			altar.announced = true;
			announce(world, place, altar, by);
		}
		save();
		refresh(world, place, altar);
		return null;
	}

	private void announce(ServerWorld world, Place place, Altar altar, ServerPlayerEntity by) {
		String type = altar.event.isBlank() ? "offering.full" : altar.event;

		com.rufana.instantlore.server.OfferingEvents.fire(world, place.pos(), type, altar.title, by,
				java.util.Collections.unmodifiableMap(altar.need),
				java.util.Collections.unmodifiableMap(altar.have));

		String json = "{\"x\":" + place.pos().getX() + ",\"y\":" + place.pos().getY()
				+ ",\"z\":" + place.pos().getZ()
				+ ",\"title\":\"" + escape(altar.title) + "\""
				+ ",\"by\":\"" + (by == null ? "" : escape(by.getName().getString())) + "\"}";

		for (ServerPlayerEntity player : nearby(world, place)) {
			PlayerEvents.send(player, type, json);
		}
		world.playSound(null, place.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.7f);
		LoreDevLog.info("[offering] {} assembled by player {} → event {}",
				place.id(), by == null ? "(topped up by command)" : by.getName().getString(), type);
	}


	@Override
	public void tick(MinecraftServer server) {
		if (++sinceTick < 20) {
			return;
		}
		sinceTick = 0;

		for (Map.Entry<String, Altar> entry : new ArrayList<>(altars.entrySet())) {
			Place place = places.get(entry.getKey());
			if (place == null) {
				continue;
			}
			ServerWorld world = worldOf(server, place.dimension());
			if (world == null) {
				continue;
			}

			Altar altar = entry.getValue();

			if (world.isChunkLoaded(ChunkPos.toLong(place.pos()))) {
				boolean ours = world.getBlockState(place.pos()).getBlock()
						instanceof com.rufana.instantlore.content.LoreBlock;
				if (!ours) {
					if (altar.orphanedAt == 0) {
						orphan(server, place, altar);
					} else if (expired(altar)) {
						forget(server, place, "grace истёк, блок так и не вернулся");
					}
					continue;
				}
				if (altar.orphanedAt != 0) {
					revive(place, altar, "блок вернулся на место");
				}
			} else if (altar.orphanedAt != 0) {
				continue;
			}

			refresh(world, place, altar);
		}
	}

	private void syncDisplays(ServerWorld world, Place place, Altar altar) {
		List<String> want = new ArrayList<>();
		for (String spec : altar.need.keySet()) {
			if (altar.have.getOrDefault(spec, 0) <= 0) {
				continue;
			}
			String item = altar.face.get(spec);
			if (item == null) {
				item = firstItem(spec);
			}
			if (item != null) {
				want.add(item);
			}
		}
		if (want.equals(altar.displayed)) {
			return;
		}

		clearDisplays(world, place);
		altar.displayed.clear();
		altar.displayed.addAll(want);

		for (int i = 0; i < want.size(); i++) {
			double dx = (i - (want.size() - 1) / 2.0) * 0.7;
			String nbt = "{item:{id:\"" + want.get(i) + "\",count:1}"
					+ ",item_display:\"ground\""
					+ ",billboard:\"vertical\""
					+ ",brightness:{sky:15,block:15}"
					+ ",transformation:{scale:[1.15f,1.15f,1.15f],translation:[0f,0f,0f]"
					+ ",left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}"
					+ ",Tags:[\"" + displayTag(place) + "\"]}";
			String cmd = "summon minecraft:item_display "
					+ (place.pos().getX() + 0.5 + dx) + " "
					+ (place.pos().getY() + 1.05) + " "
					+ (place.pos().getZ() + 0.5) + " " + nbt;

			world.getServer().getCommandManager().parseAndExecute(
					world.getServer().getCommandSource().withSilent().withWorld(world), cmd);
		}
	}

	private void clearDisplays(ServerWorld world, Place place) {
		String tag = displayTag(place);
		net.minecraft.util.math.Box area = new net.minecraft.util.math.Box(place.pos()).expand(2.0);
		for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, area,
				e -> e instanceof net.minecraft.entity.decoration.DisplayEntity
						&& e.getCommandTags().contains(tag))) {
			entity.discard();
		}
	}

	private String displayTag(Place place) {
		return "offering_" + place.pos().getX() + "_" + place.pos().getY() + "_" + place.pos().getZ();
	}

	private void refresh(ServerWorld world, Place place, Altar altar) {
		if (altar.orphanedAt != 0) {
			return;
		}
		syncDisplays(world, place, altar);
		List<String> text = display(altar);
		Set<UUID> near = new java.util.HashSet<>();

		for (ServerPlayerEntity player : nearby(world, place)) {
			near.add(player.getUuid());
			List<String> was = altar.shown.get(player.getUuid());

			boolean resized = was == null || was.size() != text.size();

			for (int i = 0; i < text.size(); i++) {
				if (resized || !text.get(i).equals(was.get(i))) {
					PlayerEvents.send(player, "marker.set",
							markerJson(place, altar, text, i));
				}
			}
			if (was != null) {
				for (int i = text.size(); i < was.size(); i++) {
					PlayerEvents.send(player, "marker.remove",
							"{\"id\":\"" + markerId(place, i) + "\"}");
				}
			}
			altar.shown.put(player.getUuid(), text);
		}

		for (UUID uuid : new ArrayList<>(altar.shown.keySet())) {
			if (!near.contains(uuid)) {
				ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
				if (player != null) {
					hide(player, place);
				}
				altar.shown.remove(uuid);
			}
		}
	}

	private static final double LINE_STEP = 0.28;

	private static final double TEXT_BASE = 1.95;

	private String markerJson(Place place, Altar altar, List<String> text, int index) {
		double y = place.pos().getY() + TEXT_BASE + (text.size() - 1 - index) * LINE_STEP;
		boolean isProgress = altar.progress && index == text.size() - 1;
		//
		String color = isProgress
				? (altar.complete() ? "#FF4E8C57" : "#FF6E7686")
				: "#FF5C6472";

		return "{\"id\":\"" + markerId(place, index) + "\",\"type\":\"text\""
				+ ",\"x\":" + (place.pos().getX() + 0.5)
				+ ",\"y\":" + y
				+ ",\"z\":" + (place.pos().getZ() + 0.5)
				+ ",\"text\":\"" + escape(text.get(index)) + "\""
				+ ",\"color\":\"" + color + "\"}";
	}

	private List<String> display(Altar altar) {
		List<String> out = new ArrayList<>();
		if (!altar.title.isBlank()) {
			out.add(altar.title);
		}
		for (String line : altar.lines) {
			if (!line.isBlank()) {
				out.add(line);
			}
		}
		if (altar.progress) {
			out.add(summary(altar));
		}
		return out;
	}

	private void hide(ServerPlayerEntity player, Place place) {
		for (int i = 0; i < MAX_LINES + 2; i++) {
			PlayerEvents.send(player, "marker.remove", "{\"id\":\"" + markerId(place, i) + "\"}");
		}
	}

	private String markerId(Place place, int index) {
		return "offering_" + place.pos().getX() + "_" + place.pos().getY() + "_"
				+ place.pos().getZ() + "_" + index;
	}

	private List<ServerPlayerEntity> nearby(ServerWorld world, Place place) {
		double r2 = (double) radius * radius;
		return world.getPlayers(p -> p.squaredDistanceTo(
				place.pos().getX() + 0.5, place.pos().getY() + 0.5, place.pos().getZ() + 0.5) <= r2);
	}

	private String summary(Altar altar) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, Integer> e : altar.need.entrySet()) {
			if (!first) {
				sb.append(" · ");
			}
			first = false;
			sb.append(altar.have.getOrDefault(e.getKey(), 0)).append('/').append(e.getValue());
		}
		return sb.toString();
	}

	private String recipe(Altar altar) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, Integer> e : altar.need.entrySet()) {
			if (!first) {
				sb.append(" · ");
			}
			first = false;
			sb.append(specName(e.getKey())).append(' ')
					.append(altar.have.getOrDefault(e.getKey(), 0)).append('/').append(e.getValue());
		}
		return sb.toString();
	}

	private String shortName(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed != null && Registries.ITEM.containsId(parsed)) {
			return Registries.ITEM.get(parsed).getName().getString();
		}
		return parsed == null ? id : parsed.getPath();
	}


	private static final int MAX_VARIANTS = 16;

	private static List<String> variants(String spec) {
		List<String> out = new ArrayList<>();
		for (String part : spec.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		return out;
	}

	private static boolean matches(String spec, ItemStack stack) {
		for (String variant : variants(spec)) {
			if (variant.startsWith("#")) {
				Identifier id = Identifier.tryParse(variant.substring(1));
				if (id != null && stack.isIn(TagKey.of(RegistryKeys.ITEM, id))) {
					return true;
				}
			} else {
				Identifier id = Identifier.tryParse(variant);
				if (id != null && Registries.ITEM.getId(stack.getItem()).equals(id)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String canonical(String spec, StringBuilder problem) {
		List<String> parts = variants(spec);
		if (parts.isEmpty()) {
			problem.append("Не сказано, что принимать");
			return null;
		}
		if (parts.size() > MAX_VARIANTS) {
			problem.append("Вариантов в одном требовании — не больше ").append(MAX_VARIANTS);
			return null;
		}
		for (String part : parts) {
			if (part.startsWith("#")) {
				Identifier id = Identifier.tryParse(part.substring(1));
				if (id == null) {
					problem.append("Не разобрать тег: ").append(part);
					return null;
				}
			} else {
				Identifier id = Identifier.tryParse(part);
				if (id == null || !Registries.ITEM.containsId(id)) {
					problem.append("Нет такого предмета: ").append(part);
					return null;
				}
			}
		}
		return String.join(",", parts);
	}

	private static String firstItem(String spec) {
		for (String variant : variants(spec)) {
			if (variant.startsWith("#")) {
				continue;
			}
			Identifier id = Identifier.tryParse(variant);
			if (id != null && Registries.ITEM.containsId(id)) {
				return id.toString();
			}
		}
		return null;
	}

	private String specName(String spec) {
		List<String> names = new ArrayList<>();
		for (String variant : variants(spec)) {
			if (variant.startsWith("#")) {
				Identifier id = Identifier.tryParse(variant.substring(1));
				names.add("#" + (id == null ? variant.substring(1) : id.getPath()));
			} else {
				names.add(shortName(variant));
			}
		}
		return String.join(" / ", names);
	}


	public String define(ServerWorld world, BlockPos pos, String spec, int count, String event) {
		StringBuilder problem = new StringBuilder();
		String key = canonical(spec, problem);
		if (key == null) {
			return problem.toString();
		}
		if (count < 1 || count > maxNeed) {
			return "Сколько — от 1 до " + maxNeed;
		}

		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			if (altars.size() >= maxAltars) {
				return "Достигнут потолок жертвенников (" + maxAltars + ")";
			}
			altar = new Altar();
			altars.put(place.id(), altar);
			places.put(place.id(), place);
		}
		altar.need.put(key, count);
		altar.announced = false;
		if (event != null) {
			altar.event = event;
		}
		save();
		refresh(world, place, altar);
		return null;
	}

	public String applyPreset(ServerWorld world, BlockPos pos, String name) {
		JsonObject preset = presets.get(name);
		if (preset == null) {
			return "Нет пресета «" + name + "». Есть: "
					+ (presets.isEmpty() ? "ни одного" : String.join(", ", presets.keySet()));
		}
		if (!preset.has("accept") || !preset.get("accept").isJsonObject()) {
			return "У пресета «" + name + "» нет объекта accept";
		}

		clear(world, pos);
		String problem = null;
		for (Map.Entry<String, JsonElement> e : preset.getAsJsonObject("accept").entrySet()) {
			int count;
			try {
				count = e.getValue().getAsInt();
			} catch (RuntimeException ex) {
				problem = "У пресета «" + name + "» нечисловое количество для " + e.getKey();
				continue;
			}
			String failed = define(world, pos, e.getKey(), count,
					preset.has("event") ? preset.get("event").getAsString() : null);
			if (failed != null) {
				problem = failed;
			}
		}

		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		if (altar != null) {
			if (preset.has("title")) {
				altar.title = preset.get("title").getAsString();
			}
			if (preset.has("lines") && preset.get("lines").isJsonArray()) {
				altar.lines.clear();
				for (JsonElement line : preset.getAsJsonArray("lines")) {
					if (altar.lines.size() < MAX_LINES) {
						altar.lines.add(line.getAsString());
					}
				}
			}
			if (preset.has("progress")) {
				altar.progress = preset.get("progress").getAsBoolean();
			}
			save();
			refresh(world, new Place(dimensionOf(world), pos), altar);
		}
		return problem;
	}

	public String addLine(ServerWorld world, BlockPos pos, String line) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		if (altar.lines.size() >= MAX_LINES) {
			return "Уже " + MAX_LINES + " строк — больше не поместится";
		}
		altar.lines.add(line);
		save();
		refresh(world, new Place(dimensionOf(world), pos), altar);
		return null;
	}

	public String removeLine(ServerWorld world, BlockPos pos, int number) {
		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		if (number < 1 || number > altar.lines.size()) {
			return altar.lines.isEmpty()
					? "Строк текста здесь нет"
					: "Строка есть только с 1 по " + altar.lines.size();
		}
		altar.lines.remove(number - 1);
		save();
		refresh(world, place, altar);
		return null;
	}

	public String setLine(ServerWorld world, BlockPos pos, int number, String line) {
		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		if (number < 1 || number > altar.lines.size()) {
			return altar.lines.isEmpty()
					? "Строк текста здесь нет — сперва line add"
					: "Строка есть только с 1 по " + altar.lines.size();
		}
		altar.lines.set(number - 1, line);
		save();
		refresh(world, place, altar);
		return null;
	}

	public List<String> lines(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		return altar == null ? null : List.copyOf(altar.lines);
	}

	public String clearLines(ServerWorld world, BlockPos pos) {
		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		for (UUID uuid : altar.shown.keySet()) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player != null) {
				hide(player, place);
			}
		}
		altar.shown.clear();
		altar.lines.clear();
		save();
		refresh(world, place, altar);
		return null;
	}

	public String setProgress(ServerWorld world, BlockPos pos, boolean on) {
		Place place = new Place(dimensionOf(world), pos);
		Altar altar = altars.get(place.id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		for (UUID uuid : altar.shown.keySet()) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
			if (player != null) {
				hide(player, place);
			}
		}
		altar.shown.clear();
		altar.progress = on;
		save();
		refresh(world, place, altar);
		return null;
	}

	public String setTitle(ServerWorld world, BlockPos pos, String title) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		if (altar == null) {
			return "Здесь не настроен жертвенник";
		}
		altar.title = title;
		save();
		refresh(world, new Place(dimensionOf(world), pos), altar);
		return null;
	}

	public boolean clear(ServerWorld world, BlockPos pos) {
		Place place = new Place(dimensionOf(world), pos);
		if (!altars.containsKey(place.id())) {
			return false;
		}
		forget(world.getServer(), place, "снят командой");
		return true;
	}

	private void orphan(MinecraftServer server, Place place, Altar altar) {
		altar.orphanedAt = System.currentTimeMillis();
		ServerWorld world = worldOf(server, place.dimension());
		if (world != null) {
			clearDisplays(world, place);
		}
		hideFromAll(server, place, altar);
		save();

		if (orphanGraceMinutes == 0) {
			forget(server, place, "блок пропал, grace выключен");
			return;
		}
		String line = "[жертвенник] " + place.id() + " — блок пропал, запись усыплена на "
				+ orphanGraceMinutes + " мин: " + recipe(altar)
				+ (altar.shares.isEmpty() ? "" : ", вкладчиков " + altar.shares.size());
		InstantLore.LOGGER.warn(line);
		LoreDevLog.info(line);
	}

	private void revive(Place place, Altar altar, String why) {
		altar.orphanedAt = 0;
		altar.shown.clear();
		altar.displayed.clear();
		save();
		String line = "[жертвенник] " + place.id() + " — " + why + ", запись восстановлена: "
				+ recipe(altar);
		InstantLore.LOGGER.info(line);
		LoreDevLog.info(line);
	}

	private boolean expired(Altar altar) {
		return altar.orphanedAt != 0
				&& System.currentTimeMillis() - altar.orphanedAt >= orphanGraceMinutes * 60_000L;
	}

	private void forget(MinecraftServer server, Place place, String why) {
		ServerWorld world = worldOf(server, place.dimension());
		if (world != null) {
			clearDisplays(world, place);
		}
		Altar altar = altars.remove(place.id());
		places.remove(place.id());
		if (altar != null) {
			hideFromAll(server, place, altar);
			String line = "[жертвенник] " + place.id() + " — запись стёрта (" + why + "): "
					+ recipe(altar)
					+ (altar.shares.isEmpty() ? "" : ", вкладчиков " + altar.shares.size()
							+ " — " + String.join("; ", shareLines(altar)));
			InstantLore.LOGGER.warn(line);
			LoreDevLog.info(line);
		}
		save();
	}

	private void hideFromAll(MinecraftServer server, Place place, Altar altar) {
		for (UUID uuid : altar.shown.keySet()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			if (player != null) {
				hide(player, place);
			}
		}
		altar.shown.clear();
	}

	private static List<String> shareLines(Altar altar) {
		List<String> out = new ArrayList<>();
		altar.shares.forEach((uuid, items) ->
				out.add(altar.names.getOrDefault(uuid, uuid.toString()) + " " + total(items)));
		return out;
	}

	public String info(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		if (altar == null) {
			return null;
		}
		return (altar.title.isBlank() ? "" : altar.title + " | ")
				+ (altar.orphanedAt == 0 ? "" : "⚠ блока нет, " + graceLeft(altar) + " | ")
				+ recipe(altar)
				+ (altar.lines.isEmpty() ? "" : " | строк текста: " + altar.lines.size())
				+ (altar.shares.isEmpty() ? "" : " | вкладчиков: " + altar.shares.size())
				+ (altar.progress ? "" : " | счёт скрыт")
				+ (altar.event.isBlank() ? "" : " → событие " + altar.event)
				+ (altar.announced ? " (объявлено)" : "");
	}

	public boolean isFull(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		return altar != null && altar.complete();
	}

	public boolean exists(ServerWorld world, BlockPos pos) {
		return altars.containsKey(new Place(dimensionOf(world), pos).id());
	}

	public String titleOf(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		return altar == null ? "" : altar.title;
	}

	public String summaryOf(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		return altar == null ? null : summary(altar);
	}

	public List<String> score(ServerWorld world, BlockPos pos) {
		Altar altar = altars.get(new Place(dimensionOf(world), pos).id());
		if (altar == null) {
			return null;
		}

		List<Map.Entry<UUID, Map<String, Integer>>> rows = new ArrayList<>(altar.shares.entrySet());
		rows.sort((a, b) -> Integer.compare(total(b.getValue()), total(a.getValue())));

		List<String> out = new ArrayList<>();
		int place = 0;
		for (Map.Entry<UUID, Map<String, Integer>> row : rows) {
			place++;
			String name = altar.names.getOrDefault(row.getKey(), row.getKey().toString());
			StringBuilder sb = new StringBuilder();
			sb.append(place).append(". ").append(name).append(" — ").append(total(row.getValue()));
			if (altar.need.size() > 1) {
				sb.append(" (");
				boolean first = true;
				for (Map.Entry<String, Integer> item : row.getValue().entrySet()) {
					if (!first) {
						sb.append(", ");
					}
					first = false;
					sb.append(shortName(item.getKey())).append(' ').append(item.getValue());
				}
				sb.append(')');
			}
			out.add(sb.toString());
		}
		return out;
	}

	private static int total(Map<String, Integer> items) {
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}

	public List<String> list() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Altar> e : altars.entrySet()) {
			Altar altar = e.getValue();
			out.add(e.getKey() + " — " + recipe(altar)
					+ (altar.orphanedAt == 0 ? "" : " ⚠ блока нет, " + graceLeft(altar)));
		}
		return out;
	}

	private String graceLeft(Altar altar) {
		long left = orphanGraceMinutes * 60_000L - (System.currentTimeMillis() - altar.orphanedAt);
		if (left <= 0) {
			return "срок вышел, сотрётся при следующей проверке";
		}
		long minutes = left / 60_000L;
		return minutes >= 60
				? "ждёт возврата ещё " + (minutes / 60) + " ч " + (minutes % 60) + " мин"
				: "ждёт возврата ещё " + minutes + " мин";
	}


	private synchronized void save() {
		JsonArray array = new JsonArray();
		for (Map.Entry<String, Altar> entry : altars.entrySet()) {
			Place place = places.get(entry.getKey());
			if (place == null) {
				continue;
			}
			Altar altar = entry.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("dimension", place.dimension());
			o.addProperty("x", place.pos().getX());
			o.addProperty("y", place.pos().getY());
			o.addProperty("z", place.pos().getZ());
			o.addProperty("title", altar.title);
			o.addProperty("event", altar.event);
			o.addProperty("announced", altar.announced);
			o.addProperty("progress", altar.progress);
			if (altar.orphanedAt != 0) {
				o.addProperty("orphaned_at", altar.orphanedAt);
			}
			JsonArray lines = new JsonArray();
			altar.lines.forEach(lines::add);
			o.add("lines", lines);
			JsonObject need = new JsonObject();
			altar.need.forEach(need::addProperty);
			o.add("need", need);
			JsonObject have = new JsonObject();
			altar.have.forEach(have::addProperty);
			o.add("have", have);
			JsonObject face = new JsonObject();
			altar.face.forEach(face::addProperty);
			o.add("face", face);
			JsonArray shares = new JsonArray();
			altar.shares.forEach((uuid, items) -> {
				JsonObject share = new JsonObject();
				share.addProperty("uuid", uuid.toString());
				share.addProperty("name", altar.names.getOrDefault(uuid, ""));
				JsonObject got = new JsonObject();
				items.forEach(got::addProperty);
				share.add("items", got);
				shares.add(share);
			});
			o.add("shares", shares);
			array.add(o);
		}

		try {
			Files.createDirectories(STORAGE.getParent());
			Path tmp = STORAGE.resolveSibling("offering.json.tmp");
			Files.writeString(tmp, array.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, STORAGE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			InstantLore.LOGGER.error("Failed to write offering.json: {}", e.toString());
		}
	}

	private void load() {
		if (!Files.exists(STORAGE)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(STORAGE, StandardCharsets.UTF_8));
			if (!parsed.isJsonArray()) {
				InstantLore.LOGGER.error("offering.json - expected an array, found: {}", parsed);
				return;
			}
			for (JsonElement element : parsed.getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				Place place = new Place(o.get("dimension").getAsString(),
						new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
				Altar altar = new Altar();
				altar.title = o.has("title") ? o.get("title").getAsString() : "";
				altar.event = o.has("event") ? o.get("event").getAsString() : "";
				altar.announced = o.has("announced") && o.get("announced").getAsBoolean();
				altar.progress = !o.has("progress") || o.get("progress").getAsBoolean();
				altar.orphanedAt = o.has("orphaned_at") ? o.get("orphaned_at").getAsLong() : 0L;
				if (o.has("lines")) {
					o.getAsJsonArray("lines").forEach(e -> altar.lines.add(e.getAsString()));
				}
				if (o.has("need")) {
					o.getAsJsonObject("need").entrySet()
							.forEach(e -> altar.need.put(e.getKey(), e.getValue().getAsInt()));
				}
				if (o.has("have")) {
					o.getAsJsonObject("have").entrySet()
							.forEach(e -> altar.have.put(e.getKey(), e.getValue().getAsInt()));
				}
				if (o.has("face")) {
					o.getAsJsonObject("face").entrySet()
							.forEach(e -> altar.face.put(e.getKey(), e.getValue().getAsString()));
				}
				if (o.has("shares")) {
					for (JsonElement shareElement : o.getAsJsonArray("shares")) {
						JsonObject share = shareElement.getAsJsonObject();
						UUID uuid = UUID.fromString(share.get("uuid").getAsString());
						Map<String, Integer> items = new ConcurrentHashMap<>();
						share.getAsJsonObject("items").entrySet()
								.forEach(e -> items.put(e.getKey(), e.getValue().getAsInt()));
						altar.shares.put(uuid, items);
						altar.names.put(uuid, share.get("name").getAsString());
					}
				}
				altars.put(place.id(), altar);
				places.put(place.id(), place);
			}
			long orphans = altars.values().stream().filter(a -> a.orphanedAt != 0).count();
			InstantLore.LOGGER.info("Offerings loaded: {}{}", altars.size(),
					orphans == 0 ? "" : " (no block, awaiting return: " + orphans + ")");
		} catch (IOException | RuntimeException e) {
			InstantLore.LOGGER.error("Failed to read offering.json: {}", e.toString());
		}
	}


	private static String dimensionOf(World world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static ServerWorld worldOf(MinecraftServer server, String dimension) {
		for (ServerWorld world : server.getWorlds()) {
			if (dimensionOf(world).equals(dimension)) {
				return world;
			}
		}
		return null;
	}

	private static String readString(JsonObject config, String key) {
		try {
			return config.get(key).getAsString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static int readInt(JsonObject config, String key, int fallback) {
		try {
			return config.has(key) ? config.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int lo, int hi) {
		return Math.max(lo, Math.min(hi, value));
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
