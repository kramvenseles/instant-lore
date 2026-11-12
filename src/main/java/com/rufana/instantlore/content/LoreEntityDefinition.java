package com.rufana.instantlore.content;

public record LoreEntityDefinition(
		String name,
		String title,
		int blockSlot,
		double size,
		double width,
		double height,
		double scale,
		double health,
		double speed,
		boolean wander,
		boolean look,
		boolean gravity,
		boolean invulnerable,
		boolean clickable,
		boolean collidable
) {
	public String displayName() {
		return title == null || title.isBlank() ? name : title;
	}

	public double extent() {
		return Math.max(width, height);
	}

	public static final LoreEntityDefinition EMPTY = new LoreEntityDefinition(
			"empty", "", 0, 1.0, 1.0, 1.0, 1.0, 20.0, 0.25, false, false, true, false, true, false);
}
