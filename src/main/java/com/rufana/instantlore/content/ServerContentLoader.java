package com.rufana.instantlore.content;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.ContentSource;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

public final class ServerContentLoader {
	private ServerContentLoader() {
	}

	public static void reload(MinecraftServer server, Runnable onDone) {
		CompletableFuture.runAsync(() -> {
			try {
				ContentSource.refreshSources();

				ContentManifest manifest =
						ContentManifest.parse(ContentSource.fetchManifest().json());

				String modulesJson = ContentSource.fetchModules();

				server.execute(() -> {
					ContentApplier.apply(manifest);
					com.rufana.instantlore.server.module.ModuleRegistry.applyConfig(server, modulesJson);

					int touched = 0;

					for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
						touched += ContentApplier.refreshDimensions(world.iterateEntities());
					}

					if (touched > 0) {
						InstantLore.LOGGER.debug("Dimensions recomputed for {} pool entities", touched);
					}

					ServerContentState.setVersion(manifest.version());
					onDone.run();
				});
			} catch (Exception e) {
				InstantLore.LOGGER.error("Failed to load content manifest", e);
				server.execute(onDone);
			}
		});
	}
}
