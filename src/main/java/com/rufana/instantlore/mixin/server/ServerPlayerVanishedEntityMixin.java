package com.rufana.instantlore.mixin.server;

import me.drex.vanish.util.VanishedEntity;

import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerVanishedEntityMixin implements VanishedEntity {
}
