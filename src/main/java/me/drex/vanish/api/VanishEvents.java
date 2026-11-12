/*
 * Compatibility shim for the melius-vanish / Simple Voice Chat vanish API
 * (original API by Drex). These types re-declare the public API surface under
 * its original package so that other mods compiling against it resolve, while
 * the implementation delegates to this mod's own InvisibleModule.
 */
package me.drex.vanish.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VanishEvents {

	private VanishEvents() {
	}

	@FunctionalInterface
	public interface VanishEvent {
		void onVanish(ServerPlayerEntity player, boolean vanish);
	}

	public static final Event<VanishEvent> VANISH_EVENT = EventFactory.createArrayBacked(
			VanishEvent.class,
			listeners -> (player, vanish) -> {
				for (VanishEvent listener : listeners) {
					try {
						listener.onVanish(player, vanish);
					} catch (Throwable t) {
						com.rufana.instantlore.InstantLore.LOGGER.warn(
								"[vanish-api] subscriber failed on onVanish", t);
					}
				}
			});
}
