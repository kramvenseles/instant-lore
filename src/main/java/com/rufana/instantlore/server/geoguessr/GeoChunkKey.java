package com.rufana.instantlore.server.geoguessr;

public final class GeoChunkKey {

	private GeoChunkKey() {
	}

	public static long of(int cx, int cz) {
		return (cx & 0xFFFFFFFFL) | ((long) cz << 32);
	}

	public static int cx(long key) {
		return (int) (key & 0xFFFFFFFFL);
	}

	public static int cz(long key) {
		return (int) (key >> 32);
	}
}
