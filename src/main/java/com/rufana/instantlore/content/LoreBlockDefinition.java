package com.rufana.instantlore.content;

import net.minecraft.sound.BlockSoundGroup;

public record LoreBlockDefinition(
		String name,
		String title,
		float hardness,
		float resistance,
		int luminance,
		BlockSoundGroup sound,
		boolean solid,
		boolean container,
		String label,
		double tick,
		String drop,
		String tool,
		double[] shape,
		boolean outline,
		boolean firework
) {
	public boolean needsBlockEntity() {
		return container || (label != null && !label.isBlank()) || tick > 0;
	}

	public String displayName() {
		return title == null || title.isBlank() ? name : title;
	}

	public static final LoreBlockDefinition EMPTY =
			new LoreBlockDefinition("empty", "empty", 1.0F, 1.0F, 0, BlockSoundGroup.STONE, true,
					false, "", 0.0, "self", "", null, true, false);
}
