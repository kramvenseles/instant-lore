package com.rufana.instantlore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mixin(SocialInteractionsScreen.class)
public abstract class SocialInteractionsHideVanishedMixin {
	@ModifyExpressionValue(method = "setCurrentTab",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getPlayerUuids()Ljava/util/Collection;"))
	private Collection<UUID> instantlore$onlyListedUuids(Collection<UUID> uuids) {
		ClientPlayNetworkHandler handler = handler();
		if (handler == null || uuids == null || uuids.isEmpty()) {
			return uuids;
		}

		List<UUID> out = new ArrayList<>(uuids.size());
		for (UUID uuid : uuids) {
			PlayerListEntry entry = handler.getPlayerListEntry(uuid);
			if (entry == null || handler.getListedPlayerListEntries().contains(entry)) {
				out.add(uuid);
			}
		}
		return out;
	}

	@ModifyExpressionValue(method = "updateServerLabel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getPlayerList()Ljava/util/Collection;"))
	private Collection<PlayerListEntry> instantlore$onlyListedEntries(
			Collection<PlayerListEntry> entries) {
		ClientPlayNetworkHandler handler = handler();
		if (handler == null || entries == null || entries.isEmpty()) {
			return entries;
		}

		Collection<PlayerListEntry> listed = handler.getListedPlayerListEntries();
		List<PlayerListEntry> out = new ArrayList<>(entries.size());
		for (PlayerListEntry entry : entries) {
			if (listed.contains(entry)) {
				out.add(entry);
			}
		}
		return out;
	}

	@Inject(method = "setPlayerOnline", at = @At("HEAD"), cancellable = true)
	private void instantlore$skipUnlisted(PlayerListEntry entry, CallbackInfo ci) {
		ClientPlayNetworkHandler handler = handler();
		if (handler != null && entry != null && !handler.getListedPlayerListEntries().contains(entry)) {
			ci.cancel();
		}
	}

	private static ClientPlayNetworkHandler handler() {
		return MinecraftClient.getInstance().getNetworkHandler();
	}
}
