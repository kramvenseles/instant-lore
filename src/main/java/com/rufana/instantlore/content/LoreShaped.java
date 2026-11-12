package com.rufana.instantlore.content;

public interface LoreShaped {
	int loreSlot();

	default LoreEntityDefinition loreDefinition() {
		return LoreContent.entity(loreSlot());
	}

	default int appearanceBlockSlot() {
		return loreDefinition().blockSlot();
	}

	default float renderPitch() {
		return 0.0F;
	}

	default float renderRoll() {
		return 0.0F;
	}
}
