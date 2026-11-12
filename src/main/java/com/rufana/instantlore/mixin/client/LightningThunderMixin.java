package com.rufana.instantlore.mixin.client;

import net.minecraft.entity.LightningEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightningEntity.class)
public abstract class LightningThunderMixin {

	private static final long QUIET_TICKS = 20L;

	private static long instantlore$lastThunder = Long.MIN_VALUE;

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;playSoundClient(DDDLnet/minecraft/sound/SoundEvent;"
					+ "Lnet/minecraft/sound/SoundCategory;FFZ)V"))
	private void instantlore$throttleThunder(World world, double x, double y, double z,
			SoundEvent sound, SoundCategory category, float volume, float pitch, boolean useDistance) {
		long now = world.getTime();
		if (now < instantlore$lastThunder || now - instantlore$lastThunder >= QUIET_TICKS) {
			instantlore$lastThunder = now;
			world.playSoundClient(x, y, z, sound, category, volume, pitch, useDistance);
		}
	}
}
