package com.rufana.instantlore.client.compat;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.LimboTransfer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.lang.reflect.Method;

public final class XaeroLimboGate {
	private static final Identifier LIMBO_ID = LimboTransfer.LIMBO_KEY.getValue();

	private static final String DIM_PATH = LIMBO_ID.getPath().replace('/', '%');

	public static final String WORLD_MAP_DIR = LIMBO_ID.getNamespace() + "$" + DIM_PATH;

	public static final String MINIMAP_DIR = "dim%" + LIMBO_ID.getNamespace() + "$" + DIM_PATH;

	private XaeroLimboGate() {
	}

	public static boolean isLimbo(World world) {
		return world != null && world.getRegistryKey().equals(LimboTransfer.LIMBO_KEY);
	}

	//
	//     mapProcessor.getMapWorld().getCurrentDimension().getLayeredMapRegions()
	//

	private static volatile Method mGetMapProcessor;     // MapWriter.getMapProcessor()
	private static volatile Method mGetMapWorld;         // MapProcessor.getMapWorld()
	private static volatile Method mGetCurrentDimension; // MapWorld.getCurrentDimension()
	private static volatile Method mGetDimId;            // MapDimension.getDimId() : RegistryKey<World>
	private static volatile boolean reflectBroken;
	private static volatile String reflectError;

	//
	private static volatile Method mRegionGetDim;      // MapRegion.getDim() : MapDimension
	private static volatile Method mRegionDimGetDimId; // MapDimension.getDimId() : RegistryKey<World>
	private static volatile boolean saveReflectBroken;
	private static volatile String saveReflectError;

	public static boolean isGuardBlind() {
		return reflectBroken || saveReflectBroken;
	}

	public static String guardBlindReason() {
		return reflectError != null ? reflectError : saveReflectError;
	}

	public static boolean destinationIsLimbo(Object mapWriter) {
		if (reflectBroken || mapWriter == null) {
			return false;
		}
		try {
			if (mGetMapProcessor == null) {
				mGetMapProcessor = mapWriter.getClass().getMethod("getMapProcessor");
			}
			Object mapProcessor = mGetMapProcessor.invoke(mapWriter);
			if (mapProcessor == null) {
				return false;
			}
			if (mGetMapWorld == null) {
				mGetMapWorld = mapProcessor.getClass().getMethod("getMapWorld");
			}
			Object mapWorld = mGetMapWorld.invoke(mapProcessor);
			if (mapWorld == null) {
				return false;
			}
			if (mGetCurrentDimension == null) {
				mGetCurrentDimension = mapWorld.getClass().getMethod("getCurrentDimension");
			}
			Object mapDimension = mGetCurrentDimension.invoke(mapWorld);
			if (mapDimension == null) {
				return false;
			}
			if (mGetDimId == null) {
				mGetDimId = mapDimension.getClass().getMethod("getDimId");
			}
			Object dimId = mGetDimId.invoke(mapDimension);
			return dimId instanceof RegistryKey<?> key && key.equals(LimboTransfer.LIMBO_KEY);
		} catch (Throwable e) {
			reflectError = e.toString();
			reflectBroken = true;
			InstantLore.LOGGER.warn("[xaero] cannot read MapWriter destination dimension "
					+ "(limbo write gate disabled until re-verified against the new Xaero): {}", e.toString());
			return false;
		}
	}

	public static boolean regionDestIsLimbo(Object region) {
		if (saveReflectBroken || region == null) {
			return false;
		}
		try {
			if (mRegionGetDim == null) {
				mRegionGetDim = region.getClass().getMethod("getDim");
			}
			Object mapDimension = mRegionGetDim.invoke(region);
			if (mapDimension == null) {
				return false;
			}
			if (mRegionDimGetDimId == null) {
				mRegionDimGetDimId = mapDimension.getClass().getMethod("getDimId");
			}
			Object dimId = mRegionDimGetDimId.invoke(mapDimension);
			return dimId instanceof RegistryKey<?> key && key.equals(LimboTransfer.LIMBO_KEY);
		} catch (Throwable e) {
			saveReflectError = e.toString();
			saveReflectBroken = true;
			InstantLore.LOGGER.warn("[xaero] cannot read region dimension in save funnel "
					+ "(limbo save-write gate disabled until re-verified against the new Xaero): {}", e.toString());
			return false;
		}
	}
}
