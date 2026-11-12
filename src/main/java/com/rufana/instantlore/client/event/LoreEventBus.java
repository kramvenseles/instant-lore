package com.rufana.instantlore.client.event;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.event.LoreEvent;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoreEventBus {
	private static final Map<String, List<Subscription>> HANDLERS = new ConcurrentHashMap<>();

	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

	private static final int MAX_DEPTH = 16;

	private LoreEventBus() {
	}

	private record Subscription(String scope, LoreEventHandler handler) {
	}

	public static void subscribe(String type, LoreEventHandler handler) {
		subscribe(null, type, handler);
	}

	public static void subscribe(String scope, String type, LoreEventHandler handler) {
		HANDLERS.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>())
				.add(new Subscription(scope, handler));
	}

	public static void clearScope(String scope) {
		HANDLERS.values().forEach(list ->
				list.removeIf(subscription -> scope.equals(subscription.scope())));
	}

	public static void publish(LoreEvent event) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.isOnThread()) {
			dispatch(event);
		} else {
			client.execute(() -> dispatch(event));
		}
	}

	private static void dispatch(LoreEvent event) {
		int depth = DEPTH.get();

		if (depth > MAX_DEPTH) {
			InstantLore.LOGGER.error("Event '{}' dropped: publish chain deeper than {} - "
					+ "the script is probably sending the event to itself", event.type(), MAX_DEPTH);
			return;
		}

		DEPTH.set(depth + 1);

		try {
			deliver(event);
		} finally {
			DEPTH.set(depth);
		}
	}

	private static void deliver(LoreEvent event) {
		List<Subscription> subscriptions = HANDLERS.get(event.type());

		if (subscriptions == null || subscriptions.isEmpty()) {
			InstantLore.LOGGER.debug("No handler for event '{}'", event.type());
			return;
		}

		for (Subscription subscription : subscriptions) {
			try {
				subscription.handler().handle(event);
			} catch (Throwable e) {
				//
				InstantLore.LOGGER.error("Event handler '{}' failed", event.type(), e);
			}
		}
	}
}
