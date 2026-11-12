package com.rufana.instantlore.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientGhosts {

	private static volatile Set<UUID> ghosts = Set.of();

	private ClientGhosts() {
	}

	public static void set(Set<UUID> next) {
		if (next == null || next.isEmpty()) {
			ghosts = Set.of();
			return;
		}
		Set<UUID> copy = ConcurrentHashMap.newKeySet(next.size());
		copy.addAll(next);
		ghosts = copy;
	}

	public static boolean contains(UUID uuid) {
		return uuid != null && ghosts.contains(uuid);
	}

	public static boolean isEmpty() {
		return ghosts.isEmpty();
	}

	public static Set<UUID> all() {
		return ghosts;
	}
}
