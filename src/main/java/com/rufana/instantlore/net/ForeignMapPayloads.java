package com.rufana.instantlore.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ForeignMapPayloads {

	public static final byte MAGIC = 42;

	private ForeignMapPayloads() {
	}

	public record JourneyMapPerms(byte[] raw) implements CustomPayload {

		public static final CustomPayload.Id<JourneyMapPerms> ID =
				new CustomPayload.Id<>(Identifier.of("journeymap", "perm_req"));

		public static final PacketCodec<RegistryByteBuf, JourneyMapPerms> CODEC =
				rawCodec(JourneyMapPerms::new, JourneyMapPerms::raw);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record VoxelMapSettings(byte[] raw) implements CustomPayload {

		public static final CustomPayload.Id<VoxelMapSettings> ID =
				new CustomPayload.Id<>(Identifier.of("voxelmap", "settings"));

		public static final PacketCodec<RegistryByteBuf, VoxelMapSettings> CODEC =
				rawCodec(VoxelMapSettings::new, VoxelMapSettings::raw);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private static <T extends CustomPayload> PacketCodec<RegistryByteBuf, T> rawCodec(
			java.util.function.Function<byte[], T> factory,
			java.util.function.Function<T, byte[]> getter) {
		return PacketCodec.of(
				(value, buf) -> buf.writeBytes(getter.apply(value)),
				buf -> {
					byte[] bytes = new byte[buf.readableBytes()];
					buf.readBytes(bytes);
					return factory.apply(bytes);
				});
	}

	public static byte[] journeyMapBody(String payloadJson) {
		PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeByte(MAGIC);
		buf.writeBoolean(false);
		buf.writeString(payloadJson);
		buf.writeBoolean(true);
		buf.writeBoolean(false);
		return copyOf(buf);
	}

	public static byte[] voxelMapBody(String settingsJson) {
		PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeByte(MAGIC);
		buf.writeString(settingsJson);
		return copyOf(buf);
	}

	private static byte[] copyOf(PacketByteBuf buf) {
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		return bytes;
	}
}
