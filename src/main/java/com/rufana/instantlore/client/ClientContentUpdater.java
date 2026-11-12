package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.ContentApplier;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.client.resource.LoreAssets;
import com.rufana.instantlore.event.LoreEvent;
import com.rufana.instantlore.event.LoreEvents;
import com.rufana.instantlore.client.script.LoreScripts;
import com.rufana.instantlore.content.ContentManifest;
import com.rufana.instantlore.net.ContentSource;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientContentUpdater {
	private static final ExecutorService WORKER =
			Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "instantlore-content");
				thread.setDaemon(true);
				return thread;
			});

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private ClientContentUpdater() {
	}

	public static void updateTo(String serverVersion) {
		if (!RUNNING.compareAndSet(false, true)) {
			InstantLore.LOGGER.debug("Content update already in progress, repeat skipped");
			return;
		}

		WORKER.execute(() -> {
			try {
				ContentSource.refreshSources();

				ContentSource.SignedManifest signed = ContentSource.fetchManifest();
				ContentManifest manifest = ContentManifest.parse(signed.json());

				if (!manifest.version().equals(serverVersion)) {
					InstantLore.LOGGER.warn("CDN serves version '{}', server expects '{}' - update postponed",
							manifest.version(), serverVersion);
					RUNNING.set(false);
					return;
				}

				int downloaded = ContentCache.sync(manifest, signed);

				MinecraftClient.getInstance().execute(() -> {
					try {
						apply(manifest, downloaded);
					} finally {
						RUNNING.set(false);
					}
				});
			} catch (Exception e) {
				InstantLore.LOGGER.error("Failed to update content", e);
				RUNNING.set(false);
			}
		});
	}

	private static void apply(ContentManifest manifest, int downloaded) {
		ContentApplier.apply(manifest);

		net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient
				.getInstance().world;

		if (world != null) {
			int touched = ContentApplier.refreshDimensions(world.getEntities());

			if (touched > 0) {
				InstantLore.LOGGER.info("Dimensions recomputed for {} pool entities", touched);
			}
		}

		LoreAssets.rebuild(manifest);
		runScripts();
		ClientContentState.setVersion(manifest.version());

		InstantLore.LOGGER.info("Content updated to '{}', files downloaded: {} - reloading resources",
				manifest.version(), downloaded);

		MinecraftClient.getInstance().reloadResources().thenRun(() -> {
			InstantLore.LOGGER.info("Resources reloaded");
			LoreHealth.send();

			LoreEvent event = LoreEvent.of(LoreEvents.CONTENT_UPDATED);
			event.data().addProperty("version", manifest.version());
			LoreEventBus.publish(event);
		});
	}

	private static void runScripts() {
		LoreScripts.reset();
		com.rufana.instantlore.client.world.WorldMarkers.clear();

		LoreAssets.under("scripts/lib/").forEach((path, sha256) -> {
			try {
				LoreScripts.addLibrary(path, ContentCache.readAsset(sha256));
			} catch (Exception e) {
				InstantLore.LOGGER.error("Failed to read library '{}'", path, e);
			}
		});

		LoreAssets.under("scripts/").forEach((path, sha256) -> {
			if (path.startsWith("scripts/lib/")) {
				return;
			}
			try {
				LoreScripts.run(path, ContentCache.readAsset(sha256));
			} catch (Exception e) {
				InstantLore.LOGGER.error("Failed to start script '{}'", path, e);
			}
		});
	}

	public static void loadFromCache() {
		ContentManifest cached = ContentCache.loadCachedManifest();

		if (cached == null) {
			InstantLore.LOGGER.info("No local content - waiting for the version from the server");
			return;
		}

		ContentApplier.apply(cached);
		LoreAssets.rebuild(cached);
		runScripts();
		ClientContentState.setVersion(cached.version());

	}
}
