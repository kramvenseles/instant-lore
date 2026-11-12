package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.resource.LoreModelCheck;
import com.rufana.instantlore.client.script.LoreScripts;
import com.rufana.instantlore.content.ContentApplier;
import com.rufana.instantlore.net.LoreHealthPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public final class LoreHealth {
	private LoreHealth() {
	}

	public static void send() {
		try {
			if (!ClientPlayNetworking.canSend(LoreHealthPayload.ID)) {
				return;
			}

			LoreModelCheck.Result models = LoreModelCheck.report();

			LoreHealthPayload payload = new LoreHealthPayload(
					modVersion(),
					ClientContentState.version(),
					models.ok(),
					models.checked(),
					ContentApplier.soundGroupCount(),
					LoreScripts.succeeded(),
					LoreScripts.attempted());

			ClientPlayNetworking.send(payload);

			LoreModReport.send();

			ClientPassport.send();
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Failed to send health report", e);
		}
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(InstantLore.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
	}
}
