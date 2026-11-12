package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ContentVersionPayload(String version) implements CustomPayload {
	public static final CustomPayload.Id<ContentVersionPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "content_version"));

	public static final PacketCodec<RegistryByteBuf, ContentVersionPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.STRING, ContentVersionPayload::version, ContentVersionPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
