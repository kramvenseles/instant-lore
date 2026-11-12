package com.rufana.instantlore.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ClientCommandSource.class)
public abstract class CommandSourceHideVanishedMixin {

	@Shadow @Final private ClientPlayNetworkHandler networkHandler;

	@ModifyReturnValue(method = "getPlayerNames", at = @At("RETURN"))
	private Collection<String> instantlore$onlyListedNames(Collection<String> names) {
		if (names == null || names.isEmpty() || this.networkHandler == null) {
			return names;
		}

		Set<String> allowed = new HashSet<>();
		for (PlayerListEntry entry : this.networkHandler.getListedPlayerListEntries()) {
			allowed.add(entry.getProfile().name());
		}
		GameProfile self = this.networkHandler.getProfile();
		if (self != null) {
			allowed.add(self.name());
		}

		List<String> out = new ArrayList<>(names.size());
		for (String name : names) {
			if (allowed.contains(name)) {
				out.add(name);
			}
		}
		return out.size() == names.size() ? names : out;
	}
}
