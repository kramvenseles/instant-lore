package com.rufana.instantlore.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class AltarPlaceFix {

	private static final String STELLARITY_ITEM_KEY = "stellarity:item";
	private static final String ALTAR_ITEM = "altar_of_the_sacred";

	private static final boolean STELLARITY_PRESENT =
			FabricLoader.getInstance().isModLoaded("stellarity");

	private AltarPlaceFix() {}

	public static void onPlaced(ItemPlacementContext context) {
		if (!STELLARITY_PRESENT) {
			return;
		}
		World world = context.getWorld();
		if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (!isAltarItem(context.getStack())) {
			return;
		}

		BlockPos pos = context.getBlockPos().toImmutable();
		MinecraftServer server = serverWorld.getServer();

		server.execute(() -> {
			if (!serverWorld.getBlockState(pos).isOf(Blocks.CHISELED_QUARTZ_BLOCK)) {
				return;
			}
			String cmd = "execute positioned " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
					+ " run function stellarity:mechanic/altar_of_the_sacred/place/hit_block";
			server.getCommandManager().parseAndExecute(
					server.getCommandSource().withSilent().withWorld(serverWorld), cmd);
		});
	}

	private static boolean isAltarItem(ItemStack stack) {
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		return data != null && ALTAR_ITEM.equals(data.copyNbt().getString(STELLARITY_ITEM_KEY, ""));
	}
}
