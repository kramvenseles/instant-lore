package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LoreUpdateDiagPayload(String code, String detail, String env,
                                    String swapTail, String updaterTail) implements CustomPayload {

	public static final int MAX_TAIL = 4096;

	public static final int MAX_CODE = 64;
	public static final int MAX_DETAIL = 512;
	public static final int MAX_ENV = 512;

	public static final CustomPayload.Id<LoreUpdateDiagPayload> ID =
			new CustomPayload.Id<>(Identifier.of(InstantLore.MOD_ID, "update_diag"));

	public static final PacketCodec<RegistryByteBuf, LoreUpdateDiagPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.string(MAX_CODE), LoreUpdateDiagPayload::code,
					PacketCodecs.string(MAX_DETAIL), LoreUpdateDiagPayload::detail,
					PacketCodecs.string(MAX_ENV), LoreUpdateDiagPayload::env,
					PacketCodecs.string(MAX_TAIL), LoreUpdateDiagPayload::swapTail,
					PacketCodecs.string(MAX_TAIL), LoreUpdateDiagPayload::updaterTail,
					LoreUpdateDiagPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
