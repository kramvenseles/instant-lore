package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoreUpdateStatePayload(String stagedVersion, boolean swappable, int swapAttempts)
		implements CustomPayload {

	public static final CustomPayload.Id<LoreUpdateStatePayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "update_state"));

	public static final PacketCodec<RegistryByteBuf, LoreUpdateStatePayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, LoreUpdateStatePayload::stagedVersion,
					PacketCodecs.BOOLEAN, LoreUpdateStatePayload::swappable,
					PacketCodecs.VAR_INT, LoreUpdateStatePayload::swapAttempts,
					LoreUpdateStatePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
