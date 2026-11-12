package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoreReportPayload(String type, String json) implements CustomPayload {
	public static final CustomPayload.Id<LoreReportPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "report"));

	public static final PacketCodec<RegistryByteBuf, LoreReportPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, LoreReportPayload::type,
					PacketCodecs.STRING, LoreReportPayload::json,
					LoreReportPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
