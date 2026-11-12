package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.render.LoreCoords;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mixin(DebugHud.class)
public abstract class DebugHudScrambleMixin {
	private static final long SPIN_MS = 350L;

	private static final String[] PREFIXES = {
			"XYZ:", "Block:", "Chunk:", "Facing:", "Section-relative:",
			"Targeted Block:", "Targeted Fluid:"
	};

	@ModifyVariable(method = "drawText", at = @At("HEAD"), argsOnly = true)
	private List<String> instantlore$scrambleCoords(List<String> lines) {
		if (!LoreCoords.isScrambled() || lines == null || lines.isEmpty()) {
			return lines;
		}

		List<String> out = new ArrayList<>(lines.size());

		for (String line : lines) {
			out.add(line != null && shouldScramble(line) ? scramble(line) : line);
		}

		return out;
	}

	private static boolean shouldScramble(String line) {
		String clean = line.indexOf('§') >= 0 ? line.replaceAll("§.", "") : line;

		for (String prefix : PREFIXES) {
			if (clean.startsWith(prefix)) {
				return true;
			}
		}

		return false;
	}

	private static String scramble(String line) {
		int keep = line.indexOf(':') + 1;
		long slot = System.currentTimeMillis() / SPIN_MS;
		int n = new Random(slot * 1000003L + line.substring(0, keep).hashCode()).nextInt(1000);
		return line.substring(0, keep) + String.format(" %03d", n);
	}
}
