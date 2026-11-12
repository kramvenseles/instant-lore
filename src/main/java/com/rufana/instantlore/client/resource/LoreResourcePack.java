package com.rufana.instantlore.client.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreContent;
import net.minecraft.SharedConstants;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class LoreResourcePack implements ResourcePack {
	public static final String PACK_ID = "instantlore_runtime";

	private final ResourcePackInfo info;

	public LoreResourcePack(ResourcePackInfo info) {
		this.info = info;
	}

	@Override
	public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
		if (type != ResourceType.CLIENT_RESOURCES || !InstantLore.MOD_ID.equals(id.getNamespace())) {
			return null;
		}

		String path = id.getPath();

		String sha256 = LoreAssets.sha256For(id);
		if (sha256 != null) {
			return () -> new ByteArrayInputStream(LoreAssets.read(sha256));
		}

		String generated = generate(path);
		return generated == null ? null
				: () -> new ByteArrayInputStream(generated.getBytes(StandardCharsets.UTF_8));
	}

	private static String generate(String path) {
		Integer slot = LoreModels.slotFromBlockstatePath(path);
		if (slot != null) {
			return LoreModels.blockstateJson(slot);
		}

		slot = LoreModels.slotFromModelPath(path);
		if (slot != null) {
			return LoreModels.modelJson(slot);
		}

		slot = LoreModels.slotFromItemPath(path);
		if (slot != null) {
			return LoreModels.itemJson(slot);
		}

		slot = LoreModels.slotFromPoolItemPath(path);
		if (slot != null) {
			return LoreModels.poolItemJson(slot);
		}

		slot = LoreModels.slotFromPoolItemModelPath(path);
		if (slot != null) {
			return LoreModels.poolItemModelJson(slot);
		}

		return null;
	}

	@Override
	public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
		if (type != ResourceType.CLIENT_RESOURCES || !InstantLore.MOD_ID.equals(namespace)) {
			return;
		}

		//
		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			emit(consumer, prefix, "blockstates/block_" + slot + ".json",
					LoreModels.blockstateJson(slot));
			emit(consumer, prefix, "models/block/block_" + slot + ".json",
					LoreModels.modelJson(slot));
			emit(consumer, prefix, "items/block_" + slot + ".json",
					LoreModels.itemJson(slot));
		}

		for (int slot = 0; slot < LoreContent.ITEM_POOL_SIZE; slot++) {
			emit(consumer, prefix, "items/item_" + slot + ".json",
					LoreModels.poolItemJson(slot));
			emit(consumer, prefix, "models/item/item_" + slot + ".json",
					LoreModels.poolItemModelJson(slot));
		}

		LoreAssets.forEachTexture((id, sha256) -> {
			if (id.getPath().startsWith(prefix)) {
				consumer.accept(id, () -> new ByteArrayInputStream(LoreAssets.read(sha256)));
			}
		});
	}

	private static void emit(ResultConsumer consumer, String prefix, String path, String json) {
		if (!path.startsWith(prefix)) {
			return;
		}

		if (LoreAssets.sha256For(Identifier.of(InstantLore.MOD_ID, path)) != null) {
			return;
		}

		consumer.accept(Identifier.of(InstantLore.MOD_ID, path),
				() -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	@Override
	public InputSupplier<InputStream> openRoot(String... segments) {
		if (segments.length == 1 && PACK_METADATA_NAME.equals(segments[0])) {
			String json = packMetadataJson().toString();
			return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
		}
		return null;
	}

	@Override
	public <T> T parseMetadata(ResourceMetadataSerializer<T> serializer) throws IOException {
		JsonObject root = packMetadataJson();

		if (!root.has(serializer.name())) {
			return null;
		}

		return serializer.codec()
				.parse(JsonOps.INSTANCE, root.get(serializer.name()))
				.getOrThrow(IOException::new);
	}

	private static JsonObject packMetadataJson() {
		int format = SharedConstants.getGameVersion()
				.packVersion(ResourceType.CLIENT_RESOURCES).major();

		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", format);
		pack.addProperty("min_format", format);
		pack.addProperty("max_format", format);
		pack.addProperty("description", "Instant Lore: контент, приехавший с сервера");

		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return root;
	}

	@Override
	public Set<String> getNamespaces(ResourceType type) {
		return type == ResourceType.CLIENT_RESOURCES ? Set.of(InstantLore.MOD_ID) : Set.of();
	}

	@Override
	public ResourcePackInfo getInfo() {
		return info;
	}

	@Override
	public void close() {
	}

	static JsonObject parse(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}
}
