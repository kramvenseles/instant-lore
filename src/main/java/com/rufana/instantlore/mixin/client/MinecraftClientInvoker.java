package com.rufana.instantlore.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientInvoker {
	@Invoker("handleBlockBreaking")
	void instantlore$handleBlockBreaking(boolean breaking);

	@Invoker("doAttack")
	boolean instantlore$doAttack();

	@Invoker("doItemUse")
	void instantlore$doItemUse();
}
