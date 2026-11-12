package com.rufana.instantlore.update;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.util.concurrent.TimeUnit;

public final class UpdatePreLaunch implements PreLaunchEntrypoint {

	private static final long LOCK_WAIT_SECONDS = TimeUnit.HOURS.toSeconds(12);

	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			return;
		}

		UpdateLog.line("--- запуск игры: " + System.getProperty("os.name")
				+ ", java " + System.getProperty("java.version"));

		SwapLauncher.spawnIfStaged(LOCK_WAIT_SECONDS);
	}
}
