package com.rufana.instantlore.mixin.server;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerInvoker {

	@Invoker("unloadEntity")
	void instantlore$unloadEntity(Entity entity);

	@Invoker("loadEntity")
	void instantlore$loadEntity(Entity entity);
}
