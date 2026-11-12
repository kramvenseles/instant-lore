package com.rufana.instantlore.client.resource;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.function.Consumer;

public class LoreResourcePackProvider implements ResourcePackProvider {
	@Override
	public void register(Consumer<ResourcePackProfile> consumer) {
		ResourcePackInfo info = new ResourcePackInfo(
				LoreResourcePack.PACK_ID,
				Text.literal("Instant Lore"),
				ResourcePackSource.BUILTIN,
				Optional.empty());

		ResourcePackPosition position = new ResourcePackPosition(
				true,
				ResourcePackProfile.InsertionPosition.TOP,
				true);

		ResourcePackProfile profile = ResourcePackProfile.create(
				info,
				new ResourcePackProfile.PackFactory() {
					@Override
					public ResourcePack open(ResourcePackInfo packInfo) {
						return new LoreResourcePack(packInfo);
					}

					@Override
					public ResourcePack openWithOverlays(ResourcePackInfo packInfo,
							ResourcePackProfile.Metadata metadata) {
						return new LoreResourcePack(packInfo);
					}
				},
				ResourceType.CLIENT_RESOURCES,
				position);

		if (profile != null) {
			consumer.accept(profile);
		}
	}
}
