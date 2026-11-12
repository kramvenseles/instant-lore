package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.GhostFlag;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateGhostMixin implements GhostFlag {

	@Unique
	private boolean instantlore$ghost;

	@Override
	public void instantlore$setGhost(boolean ghost) {
		this.instantlore$ghost = ghost;
	}

	@Override
	public boolean instantlore$isGhost() {
		return this.instantlore$ghost;
	}
}
