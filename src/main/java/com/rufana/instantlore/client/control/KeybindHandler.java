package com.rufana.instantlore.client.control;

import com.rufana.instantlore.client.event.LoreEventBus;
import com.rufana.instantlore.event.LoreEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class KeybindHandler {
	private static final int COUNT = 4;

	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of("instantlore", "main"));
	private static final KeyBinding[] KEYS = new KeyBinding[COUNT];

	private KeybindHandler() {
	}

	public static void register() {
		for (int i = 0; i < COUNT; i++) {
			int code = (i == 0) ? GLFW.GLFW_KEY_G : InputUtil.UNKNOWN_KEY.getCode();
			KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
					"key.instantlore." + (i + 1), InputUtil.Type.KEYSYM, code, CATEGORY));
		}

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (int i = 0; i < COUNT; i++) {
				while (KEYS[i].wasPressed()) {
					LoreEventBus.publish(LoreEvent.of("key." + (i + 1)));
				}
			}
		});
	}
}
