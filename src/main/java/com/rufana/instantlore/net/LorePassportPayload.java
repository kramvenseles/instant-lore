package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record LorePassportPayload(
		String env, List<String> mods, List<String> packs, int totalMods, int totalPacks)
		implements CustomPayload {

	public static final int SCHEMA = 2;

	public static final int MAX_MODS = 512;

	public static final int MAX_PACKS = 64;

	public static final int MAX_ENTRY_LENGTH = 192;

	public static final String SEP = "\t";

	public static final CustomPayload.Id<LorePassportPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "passport"));

	public static final PacketCodec<RegistryByteBuf, LorePassportPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.string(MAX_ENTRY_LENGTH), LorePassportPayload::env,
					PacketCodecs.string(MAX_ENTRY_LENGTH).collect(PacketCodecs.toList(MAX_MODS)),
					LorePassportPayload::mods,
					PacketCodecs.string(MAX_ENTRY_LENGTH).collect(PacketCodecs.toList(MAX_PACKS)),
					LorePassportPayload::packs,
					PacketCodecs.VAR_INT, LorePassportPayload::totalMods,
					PacketCodecs.VAR_INT, LorePassportPayload::totalPacks,
					LorePassportPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
