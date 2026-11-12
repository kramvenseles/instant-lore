/*
 * Compatibility shim for the melius-vanish / Simple Voice Chat vanish API
 * (original API by Drex). These types re-declare the public API surface under
 * its original package so that other mods compiling against it resolve, while
 * the implementation delegates to this mod's own InvisibleModule.
 */
package me.drex.vanish.util;

public interface VanishedEntity {

	default void vanish$setDirty() {
	}
}
