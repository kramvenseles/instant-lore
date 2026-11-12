package com.rufana.instantlore.mixin.client;

import com.rufana.instantlore.client.resource.LoreResourcePackProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Final
	private ResourcePackManager resourcePackManager;

	@Inject(
			method = "<init>",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/resource/ResourcePackManager;scanPacks()V"))
	private void instantlore$registerResourcePack(CallbackInfo ci) {
		Set<ResourcePackProvider> providers =
				((ResourcePackManagerAccessor) resourcePackManager).instantlore$providers();

		providers.add(new LoreResourcePackProvider());
	}
}
