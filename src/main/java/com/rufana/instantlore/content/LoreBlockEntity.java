package com.rufana.instantlore.content;

import com.rufana.instantlore.server.LoreBlockBus;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LoreBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	public static final int SIZE = 27;

	private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

	private int sinceTick;

	public LoreBlockEntity(BlockPos pos, BlockState state) {
		super(LoreBlockEntities.TYPE, pos, state);
	}

	public int slot() {
		return getCachedState().getBlock() instanceof LoreBlock block ? block.slot() : -1;
	}

	public LoreBlockDefinition definition() {
		return LoreContent.block(slot());
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, LoreBlockEntity be) {
		LoreBlockDefinition definition = be.definition();

		if (definition.tick() <= 0 || !(world instanceof ServerWorld serverWorld)) {
			return;
		}

		int period = Math.max(1, (int) Math.round(definition.tick() * 20.0));

		if (++be.sinceTick < period) {
			return;
		}

		be.sinceTick = 0;
		LoreBlockBus.tick(serverWorld, pos, be.slot(), definition);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		stacks.clear();
		Inventories.readData(view, stacks);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, stacks);
	}

	// --- Inventory ---

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return stacks.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(stacks, slot, amount);

		if (!result.isEmpty()) {
			markDirty();
		}

		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(stacks, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		stacks.set(slot, stack);
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		stacks.clear();
	}


	@Override
	public Text getDisplayName() {
		return Text.literal(definition().displayName());
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
	}
}
