package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoreHealthPayload(
		String modVersion,
		String contentVersion,
		int modelsOk,
		int modelsTotal,
		int soundGroups,
		int scriptsOk,
		int scriptsTotal
) implements CustomPayload {

	public static final CustomPayload.Id<LoreHealthPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "health"));

	public static final PacketCodec<RegistryByteBuf, LoreHealthPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING, LoreHealthPayload::modVersion,
					PacketCodecs.STRING, LoreHealthPayload::contentVersion,
					PacketCodecs.VAR_INT, LoreHealthPayload::modelsOk,
					PacketCodecs.VAR_INT, LoreHealthPayload::modelsTotal,
					PacketCodecs.VAR_INT, LoreHealthPayload::soundGroups,
					PacketCodecs.VAR_INT, LoreHealthPayload::scriptsOk,
					PacketCodecs.VAR_INT, LoreHealthPayload::scriptsTotal,
					LoreHealthPayload::new);

	public boolean healthy() {
		return modelsOk == modelsTotal && scriptsOk == scriptsTotal && soundGroups > 0;
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
