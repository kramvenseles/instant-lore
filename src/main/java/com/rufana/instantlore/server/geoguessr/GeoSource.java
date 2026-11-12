package com.rufana.instantlore.server.geoguessr;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.world.storage.VersionedChunkStorage;

import java.io.IOException;
import java.nio.file.Path;

public final class GeoSource implements AutoCloseable {

	private final VersionedChunkStorage chunks;
	private final VersionedChunkStorage entities;

	public GeoSource(MinecraftServer server, Path base) {
		var fixer = server.getDataFixer();
		StorageKey ck = new StorageKey("geoguessr-src", GeoDimension.KEY, "chunk");
		this.chunks = new VersionedChunkStorage(ck, base.resolve("region"), fixer, false, DataFixTypes.CHUNK);
		StorageKey ek = new StorageKey("geoguessr-src", GeoDimension.KEY, "entities");
		this.entities = new VersionedChunkStorage(ek, base.resolve("entities"), fixer, false, DataFixTypes.ENTITY_CHUNK);
	}

	public NbtCompound chunk(int cx, int cz) {
		return chunks.getNbt(new ChunkPos(cx, cz)).join().orElse(null);
	}

	public NbtCompound entityChunk(int cx, int cz) {
		return entities.getNbt(new ChunkPos(cx, cz)).join().orElse(null);
	}

	@Override
	public void close() throws IOException {
		try {
			chunks.close();
		} finally {
			entities.close();
		}
	}
}
