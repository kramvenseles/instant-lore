package com.rufana.instantlore.server;

import com.rufana.instantlore.InstantLore;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.List;

public final class LimboGate {

	private static final List<String> BLOCKED_PACKAGES = List.of(
			"net.blay09.mods.waystones.",      // waystones (core/WaystoneTeleportManager)
			"net.kagaries.marriagemasterport.", // marriage master (service/MarriageService)
			"com.rufana.instantlore.probe.waystones."
	);

	private static final int STACK_DEPTH = 24;

	private LimboGate() {
	}

	public static boolean blocked(ServerPlayerEntity player, RegistryKey<World> to) {
		if (player == null || to == null) {
			return false;
		}
		RegistryKey<World> from = player.getEntityWorld().getRegistryKey();
		boolean crossesBorder = from.equals(LimboTransfer.LIMBO_KEY) != to.equals(LimboTransfer.LIMBO_KEY);
		if (!crossesBorder) {
			return false;
		}

		String initiator = blockedInitiator();
		if (initiator == null) {
			traceCrossing(player, from, to);
			return false;
		}

		InstantLore.LOGGER.info("[limbo] teleport of {} across the border rejected: {} → {} ({})",
				initiator, from.getValue(), to.getValue(), player.getName().getString());
		return true;
	}

	private static void traceCrossing(ServerPlayerEntity player, RegistryKey<World> from,
			RegistryKey<World> to) {
		try {
			String frames = StackWalker.getInstance().walk(stream -> stream
					.limit(STACK_DEPTH)
					.map(StackWalker.StackFrame::getClassName)
					.filter(name -> !name.startsWith("net.minecraft.")
							&& !name.startsWith("com.rufana.instantlore.")
							&& !name.startsWith("java.")
							&& !name.startsWith("jdk."))
					.distinct()
					.limit(4)
					.reduce((a, b) -> a + " ← " + b)
					.orElse("только ваниль и мы"));
			LoreDevLog.info("[limbo] {} crossed the border ({} → {}), initiator: {}",
					player.getName().getString(), from.getValue(), to.getValue(), frames);
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[limbo] failed to record crossing trace", t);
		}
	}

	private static String blockedInitiator() {
		try {
			return StackWalker.getInstance().walk(frames -> frames
					.limit(STACK_DEPTH)
					.map(StackWalker.StackFrame::getClassName)
					.filter(name -> BLOCKED_PACKAGES.stream().anyMatch(name::startsWith))
					.findFirst()
					.orElse(null));
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("[limbo] failed to identify teleport initiator", t);
			return null;
		}
	}
}
