package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.net.ForeignMapPayloads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class MapPolicy {

	private MapPolicy() {
	}

	private static String journeyMapJson(boolean limbo) {
		String allowed = limbo ? "false" : "true";
		String scope = limbo ? "NONE" : "ALL";
		return "{"
				+ "\"journeymapEnabled\":\"true\","
				+ "\"minimapEnabled\":\"" + allowed + "\","
				+ "\"hideCoordinates\":\"" + (limbo ? "true" : "false") + "\","
				+ "\"surfaceMapping\":\"" + scope + "\","
				+ "\"topoMapping\":\"" + scope + "\","
				+ "\"biomeMapping\":\"" + scope + "\","
				+ "\"caveMapping\":\"" + scope + "\","
				+ "\"radarEnabled\":\"" + scope + "\","
				+ "\"playerRadarEnabled\":\"" + allowed + "\","
				+ "\"villagerRadarEnabled\":\"" + allowed + "\","
				+ "\"animalRadarEnabled\":\"" + allowed + "\","
				+ "\"mobRadarEnabled\":\"" + allowed + "\","
				+ "\"allowWaypoints\":\"" + allowed + "\","
				+ "\"allowDeathPoints\":\"" + allowed + "\","
				+ "\"showInGameBeacons\":\"" + allowed + "\","
				+ "\"teleportEnabled\":\"false\","
				+ "\"crossDimTeleport\":\"false\""
				+ "}";
	}

	private static String voxelMapJson(boolean limbo) {
		boolean allow = !limbo;
		return "{"
				+ "\"worldName\":\"" + (limbo ? "limbo" : "world") + "\","
				+ "\"radarAllowed\":" + allow + ","
				+ "\"radarMobsAllowed\":" + allow + ","
				+ "\"radarPlayersAllowed\":" + allow + ","
				+ "\"cavesAllowed\":" + allow + ","
				+ "\"minimapAllowed\":" + allow + ","
				+ "\"worldmapAllowed\":" + allow + ","
				+ "\"waypointsAllowed\":" + allow + ","
				+ "\"deathWaypointAllowed\":" + allow
				+ "}";
	}

	public static void send(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		boolean limbo = player.getEntityWorld().getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
		try {
			boolean jm = ServerPlayNetworking.canSend(player, ForeignMapPayloads.JourneyMapPerms.ID);
			boolean voxel = ServerPlayNetworking.canSend(player, ForeignMapPayloads.VoxelMapSettings.ID);
			if (jm) {
				ServerPlayNetworking.send(player, new ForeignMapPayloads.JourneyMapPerms(
						ForeignMapPayloads.journeyMapBody(journeyMapJson(limbo))));
			}
			if (voxel) {
				ServerPlayNetworking.send(player, new ForeignMapPayloads.VoxelMapSettings(
						ForeignMapPayloads.voxelMapBody(voxelMapJson(limbo))));
			}
			LoreDevLog.info("[maps] policy {} for {}: journeymap={}, voxelmap={}",
					limbo ? "LIMBO" : "normal", player.getName().getString(),
					jm ? "sent" : "no channel", voxel ? "sent" : "no channel");
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[maps] failed to send map policy to player {}",
					player.getName().getString(), t);
		}
	}

	public static void onJourneyMapRequest(ServerPlayerEntity player) {
		send(player);
	}
}
