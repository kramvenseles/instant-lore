package com.rufana.instantlore.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class LoreNetworking {
	private LoreNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(ContentVersionPayload.ID, ContentVersionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LoreEventPayload.ID, LoreEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SoulMoneyPayload.ID, SoulMoneyPayload.CODEC);

		PayloadTypeRegistry.playC2S().register(LoreHealthPayload.ID, LoreHealthPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LoreReportPayload.ID, LoreReportPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LoreUpdateStatePayload.ID, LoreUpdateStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LoreUpdateDiagPayload.ID, LoreUpdateDiagPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LoreModsPayload.ID, LoreModsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LorePassportPayload.ID, LorePassportPayload.CODEC);

		registerForeignMapChannels();
	}

	private static void registerForeignMapChannels() {
		if (!isLoaded("journeymap")) {
			PayloadTypeRegistry.playS2C().register(
					ForeignMapPayloads.JourneyMapPerms.ID, ForeignMapPayloads.JourneyMapPerms.CODEC);
			PayloadTypeRegistry.playC2S().register(
					ForeignMapPayloads.JourneyMapPerms.ID, ForeignMapPayloads.JourneyMapPerms.CODEC);
		}
		if (!isLoaded("voxelmap")) {
			PayloadTypeRegistry.playS2C().register(
					ForeignMapPayloads.VoxelMapSettings.ID, ForeignMapPayloads.VoxelMapSettings.CODEC);
		}
	}

	private static boolean isLoaded(String modId) {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
	}
}
