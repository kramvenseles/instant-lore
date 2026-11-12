package com.rufana.instantlore.mixin.server;

import com.rufana.instantlore.server.module.InvisibleModule;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(SleepManager.class)
public class SleepManagerMixin {

	@ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
	private List<ServerPlayerEntity> instantlore$excludeHidden(List<ServerPlayerEntity> players) {
		return InvisibleModule.filterForSleep(players);
	}
}
