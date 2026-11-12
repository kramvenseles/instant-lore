package com.rufana.instantlore.server.integration;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.server.module.InvisibleModule;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;

import me.lucko.fabric.api.permissions.v0.Permissions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class PlaceholderCounters {

	private PlaceholderCounters() {
	}

	private static final String STAFF_PERMISSION = "tab.staff";

	public static void register() {
		Placeholders.register(Identifier.of("instantlore", "online"),
				(ctx, arg) -> ctx.hasPlayer()
						? PlaceholderResult.value(Integer.toString(count(ctx, false)))
						: PlaceholderResult.invalid());
		Placeholders.register(Identifier.of("instantlore", "staffonline"),
				(ctx, arg) -> ctx.hasPlayer()
						? PlaceholderResult.value(Integer.toString(count(ctx, true)))
						: PlaceholderResult.invalid());
		InstantLore.LOGGER.info("[pb4] counters %instantlore:online%/%instantlore:staffonline% "
				+ "registered (per-viewer, reload-safe)");
	}

	private static int count(PlaceholderContext ctx, boolean staffOnly) {
		ServerPlayerEntity viewer = ctx.player();
		MinecraftServer s = ctx.server();
		if (s == null || viewer == null) {
			return 0;
		}
		int n = 0;
		for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
			if (!InvisibleModule.tabCanSee(viewer, p.getUuid())) {
				continue;
			}
			if (staffOnly && !Permissions.check(p, STAFF_PERMISSION)) {
				continue;
			}
			n++;
		}
		return n;
	}
}
