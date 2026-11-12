package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record LoreModsPayload(List<String> mods, int total) implements CustomPayload {

	public static final int MAX_ENTRIES = 512;

	public static final int MAX_ENTRY_LENGTH = 128;

	public static final CustomPayload.Id<LoreModsPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "mods"));

	public static final PacketCodec<RegistryByteBuf, LoreModsPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.string(MAX_ENTRY_LENGTH).collect(PacketCodecs.toList(MAX_ENTRIES)),
					LoreModsPayload::mods,
					PacketCodecs.VAR_INT, LoreModsPayload::total,
					LoreModsPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
