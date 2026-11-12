package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SoulMoneyPayload(long balance) implements CustomPayload {
	public static final CustomPayload.Id<SoulMoneyPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "soul_money"));

	public static final PacketCodec<RegistryByteBuf, SoulMoneyPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.VAR_LONG, SoulMoneyPayload::balance, SoulMoneyPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
