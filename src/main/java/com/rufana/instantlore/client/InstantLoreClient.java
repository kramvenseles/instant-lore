package com.rufana.instantlore.client;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.client.compat.XaeroLeakDetector;
import com.rufana.instantlore.client.compat.XaeroLimboCachePurge;
import com.rufana.instantlore.client.compat.XaeroMigration;
import com.rufana.instantlore.client.control.PlayerControl;
import com.rufana.instantlore.client.cutscene.CutsceneHandler;
import com.rufana.instantlore.client.cutscene.CutscenePlayer;
import com.rufana.instantlore.client.dialog.DialogHandler;
import com.rufana.instantlore.client.effect.EffectHandler;
import com.rufana.instantlore.client.effect.EffectPlayer;
import com.rufana.instantlore.client.control.ControlHandler;
import com.rufana.instantlore.client.event.BuiltinHandlers;
import com.rufana.instantlore.client.hud.CrosshairHandler;
import com.rufana.instantlore.client.hud.HudHandler;
import com.rufana.instantlore.client.hud.HudRenderer;
import com.rufana.instantlore.client.narrative.NarrativeHandler;
import com.rufana.instantlore.client.bossbar.BossBarHandler;
import com.rufana.instantlore.client.sound.SoundHandler;
import com.rufana.instantlore.client.ui.ScreenHandler;
import com.rufana.instantlore.client.world.WorldMarkers;
import com.rufana.instantlore.client.script.LoreTimers;
import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.event.LoreEvent;
import com.rufana.instantlore.net.ContentSources;
import com.rufana.instantlore.net.ContentVersionPayload;
import com.rufana.instantlore.net.LoreEventPayload;
import com.rufana.instantlore.net.SoulMoneyPayload;
import com.rufana.instantlore.content.LocalSoulBalance;
import com.rufana.instantlore.client.render.CoordsHandler;
import com.rufana.instantlore.client.render.LoreOverlays;
import com.rufana.instantlore.client.render.OverlaysHandler;
import com.rufana.instantlore.client.render.LoreCoords;
import com.rufana.instantlore.client.render.LoreFog;
import com.rufana.instantlore.client.render.Desaturate;
import com.rufana.instantlore.client.render.RiftEffect;
import com.rufana.instantlore.client.render.RiftDebugOverlay;
import com.rufana.instantlore.client.render.FogHandler;
import com.rufana.instantlore.client.render.LoreSky;
import com.rufana.instantlore.client.render.LoreCamera;
import com.rufana.instantlore.client.render.TintHandler;
import com.rufana.instantlore.client.render.TintOverlay;
import com.rufana.instantlore.client.render.LoreBlockEntityRenderer;
import com.rufana.instantlore.client.render.LoreEntityRenderer;
import com.rufana.instantlore.content.LoreEntities;
import com.rufana.instantlore.content.LoreBlockEntities;
import com.rufana.instantlore.content.LoreBlocks;
import com.rufana.instantlore.content.LoreContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class InstantLoreClient implements ClientModInitializer {
	private static void registerRenderLayer() {
		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			BlockRenderLayerMap.putBlock(LoreBlocks.get(slot), BlockRenderLayer.CUTOUT);
		}
	}

	private void registerCaptureRelease() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				client.execute(() -> {
					CutscenePlayer.stop();
					PlayerControl.release("разрыв связи");

					// gameRenderer post-processor, LoreSky.hidden, LoreCoords.scrambled).
					LoreFog.clear();
					Desaturate.reset();
					LoreSky.show();
					LoreCoords.set(false);

					LoreOverlays.set(false);

					com.rufana.instantlore.client.hud.DirectionGlow.clear();

					com.rufana.instantlore.client.bossbar.BossBarHandler.clearAll();
					client.inGameHud.setOverlayMessage(net.minecraft.text.Text.empty(), false);
				}));
	}

	private static void registerRenderers() {
		BlockEntityRendererRegistry.register(LoreBlockEntities.TYPE, LoreBlockEntityRenderer::new);

		for (var type : LoreEntities.mobs()) {
			EntityRendererRegistry.register(type, LoreEntityRenderer::new);
		}

		for (var type : LoreEntities.objects()) {
			EntityRendererRegistry.register(type, LoreEntityRenderer::new);
		}
	}

	@Override
	public void onInitializeClient() {
		registerRenderLayer();
		registerRenderers();
		BuiltinHandlers.register();
		ControlHandler.register();
		com.rufana.instantlore.client.control.KeybindHandler.register();
		CutsceneHandler.register();
		CutscenePlayer.register();
		HudHandler.register();
		CrosshairHandler.register();
		HudRenderer.register();
		DialogHandler.register();
		NarrativeHandler.register();
		BossBarHandler.register();
		SoundHandler.register();
		ScreenHandler.register();
		WorldMarkers.register();
		RiftEffect.register();
		RiftDebugOverlay.register();
		EffectHandler.register();
		EffectPlayer.register();
		FogHandler.register();
		CoordsHandler.register();
		OverlaysHandler.register();
		TintHandler.register();
		TintOverlay.register();
		Desaturate.register();
		LoreSky.register();
		LoreCamera.register();
		GhostEffect.register();
		LoreTimers.register();
		registerCaptureRelease();
		ContentSources.loadFromDisk();
		ClientContentUpdater.loadFromCache();

		ClientUpdater.cleanupIfApplied();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientUpdater.spawnSwapperIfStaged());

		XaeroMigration.run();

		XaeroLimboCachePurge.run();

		XaeroLeakDetector.register();

		ClientPlayNetworking.registerGlobalReceiver(ContentVersionPayload.ID, (payload, context) -> {
			String serverVersion = payload.version();

			ClientUpdater.checkAndStage();

			CompanionUpdater.checkAndDeliver();

			ClientUpdater.reportState();

			if (ClientContentState.isStale(serverVersion)) {
				InstantLore.LOGGER.info("Content is outdated: client has '{}', server serves '{}' - downloading",
						ClientContentState.version(), serverVersion);
				ClientContentUpdater.updateTo(serverVersion);
			} else {
				InstantLore.LOGGER.info("Content is up to date: version '{}'", serverVersion);

				LoreHealth.send();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(LoreEventPayload.ID, (payload, context) -> {
			try {
				LoreEventBus.publish(LoreEvent.parse(payload.type(), payload.json()));
			} catch (Exception e) {
				InstantLore.LOGGER.error("Event '{}' failed to parse: {}", payload.type(), payload.json(), e);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SoulMoneyPayload.ID, (payload, context) ->
				LocalSoulBalance.set(payload.balance()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalSoulBalance.clear());

		InstantLore.LOGGER.info("Instant Lore: client initialization");
	}
}
