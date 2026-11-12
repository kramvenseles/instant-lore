/*
 * Compatibility shim for the melius-vanish / Simple Voice Chat vanish API
 * (original API by Drex). These types re-declare the public API surface under
 * its original package so that other mods compiling against it resolve, while
 * the implementation delegates to this mod's own InvisibleModule.
 */
package me.drex.vanish.api;

import com.rufana.instantlore.server.module.InvisibleModule;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public interface VanishAPI {

	static boolean isVanished(Entity entity) {
		return entity instanceof ServerPlayerEntity player
				&& InvisibleModule.isHidden(player.getUuid());
	}

	static boolean canSeePlayer(ServerPlayerEntity target, ServerPlayerEntity viewer) {
		boolean visible = !InvisibleModule.apiConcealedFrom(viewer, target);
		if (!visible) {
			com.rufana.instantlore.server.LoreDevLog.info("[vanish-api] hid target={} from viewer={}",
					target.getUuid().toString().substring(0, 8),
					viewer == null ? "null" : viewer.getUuid().toString().substring(0, 8));
		}
		return visible;
	}

	static boolean setVanish(ServerPlayerEntity player, boolean vanish) {
		// easyauth 3.4.3, ServerPlayerEntityMixin.easyAuth$setAuthenticated → setVanished(player,
		if (!vanish) {
			com.rufana.instantlore.server.integration.voicechat.VcReconnect.onAuthenticated(player);
		}
		return false;
	}
}
