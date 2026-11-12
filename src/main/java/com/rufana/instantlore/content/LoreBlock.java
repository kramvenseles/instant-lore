package com.rufana.instantlore.content;

import com.mojang.serialization.MapCodec;
import com.rufana.instantlore.InstantLore;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.List;

public class LoreBlock extends Block implements BlockEntityProvider {
	private static final int[] LIGHT_STEPS = {0, 5, 10, 15};

	public static final IntProperty LIGHT = IntProperty.of("light", 0, LIGHT_STEPS.length - 1);

	public static int lightLevel(int step) {
		return LIGHT_STEPS[MathHelper.clamp(step, 0, LIGHT_STEPS.length - 1)];
	}

	public static int nearestStep(int luminance) {
		int best = 0;

		for (int i = 1; i < LIGHT_STEPS.length; i++) {
			if (Math.abs(LIGHT_STEPS[i] - luminance) < Math.abs(LIGHT_STEPS[best] - luminance)) {
				best = i;
			}
		}

		return best;
	}

	public static final MapCodec<LoreBlock> CODEC = createCodec(settings -> new LoreBlock(0, settings));

	private final int slot;

	public LoreBlock(int slot, Settings settings) {
		super(settings);
		this.slot = slot;
		setDefaultState(getDefaultState().with(LIGHT, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LIGHT);
	}

	public BlockState currentState() {
		return getDefaultState().with(LIGHT, nearestStep(definition().luminance()));
	}

	public int slot() {
		return slot;
	}

	public LoreBlockDefinition definition() {
		return LoreContent.block(slot);
	}

	@Override
	public net.minecraft.text.MutableText getName() {
		return net.minecraft.text.Text.literal(definition().displayName());
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockSoundGroup getSoundGroup(BlockState state) {
		return definition().sound();
	}

	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		float hardness = definition().hardness();

		if (hardness == -1.0F) {
			return 0.0F;
		}

		int divisor = canHarvest(player.getMainHandStack()) ? 30 : 100;
		return player.getBlockBreakingSpeed(state) / hardness / (float) divisor;
	}


	public boolean canHarvest(ItemStack tool) {
		return HarvestRule.parse(definition().tool()).satisfiedBy(tool);
	}

	@Override
	protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
		LoreBlockDefinition definition = definition();

		if (!canHarvest(builder.getOptional(LootContextParameters.TOOL))) {
			return List.of();
		}

		ItemStack drop = dropStack(definition);
		return drop.isEmpty() ? List.of() : List.of(drop);
	}

	private ItemStack dropStack(LoreBlockDefinition definition) {
		String spec = definition.drop();

		if (spec == null || spec.isBlank() || spec.equals("none")) {
			return ItemStack.EMPTY;
		}

		if (spec.equals("self")) {
			return new ItemStack(LoreItems.get(slot));
		}

		int colon = spec.indexOf(':');

		if (colon > 0) {
			int other;

			try {
				other = Integer.parseInt(spec.substring(colon + 1).trim());
			} catch (NumberFormatException ignored) {
				return ItemStack.EMPTY;
			}

			String kind = spec.substring(0, colon).trim();

			if (kind.equals("item") && other >= 0 && other < LoreContent.ITEM_POOL_SIZE) {
				return new ItemStack(LoreItemPool.get(other));
			}

			if (kind.equals("block") && other >= 0 && other < LoreContent.POOL_SIZE) {
				return new ItemStack(LoreItems.get(other));
			}
		}

		return ItemStack.EMPTY;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return definition().solid() ? shape(definition()) : VoxelShapes.empty();
	}

	private static final double SOLID_AVERAGE_SIDE = 0.7291666666666666;

	public boolean solidSupport() {
		LoreBlockDefinition definition = definition();

		if (!definition.solid()) {
			return false;
		}

		double[] box = definition.shape();

		if (box == null || box.length != 6) {
			return true;
		}

		double x = Math.abs(box[3] - box[0]) / 16.0;
		double y = Math.abs(box[4] - box[1]) / 16.0;
		double z = Math.abs(box[5] - box[2]) / 16.0;

		return (x + y + z) / 3.0 >= SOLID_AVERAGE_SIDE || y >= 1.0;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		LoreBlockDefinition definition = definition();
		return definition.outline() ? shape(definition) : VoxelShapes.empty();
	}

	private static VoxelShape shape(LoreBlockDefinition definition) {
		double[] box = definition.shape();

		if (box == null || box.length != 6) {
			return VoxelShapes.fullCube();
		}

		return VoxelShapes.cuboid(
				Math.min(box[0], box[3]) / 16.0, Math.min(box[1], box[4]) / 16.0,
				Math.min(box[2], box[5]) / 16.0, Math.max(box[0], box[3]) / 16.0,
				Math.max(box[1], box[4]) / 16.0, Math.max(box[2], box[5]) / 16.0);
	}


	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return definition().needsBlockEntity() ? new LoreBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient() || definition().tick() <= 0) {
			return null;
		}

		if (type != LoreBlockEntities.TYPE) {
			return null;
		}

		@SuppressWarnings("unchecked")
		BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<LoreBlockEntity>)
				LoreBlockEntity::serverTick;
		return ticker;
	}

	@Override
	protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (dispatchUse(world, pos, player, hand, stack, hit)) {
			return ActionResult.SUCCESS;
		}

		return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (dispatchUse(world, pos, player, Hand.MAIN_HAND, ItemStack.EMPTY, hit)) {
			return ActionResult.SUCCESS;
		}

		if (!definition().container()) {
			return ActionResult.PASS;
		}

		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}

		if (world.getBlockEntity(pos) instanceof LoreBlockEntity be) {
			player.openHandledScreen(be);
			return ActionResult.SUCCESS;
		}

		return ActionResult.PASS;
	}

	private boolean dispatchUse(World world, BlockPos pos, PlayerEntity player, Hand hand,
			ItemStack stack, BlockHitResult hit) {
		if (!(world instanceof ServerWorld serverWorld)
				|| !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
			return false;
		}

		return com.rufana.instantlore.server.LoreBlockBus.use(serverPlayer, hand, stack, serverWorld,
				pos, slot(), definition(), hit.getSide(), hit.getPos(), player.isSneaking());
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof LoreBlockEntity be) {
			ItemScatterer.spawn(world, pos, be);
		}

		if (definition().firework()) {
			detonateFirework(world, pos);
		}

		super.onStateReplaced(state, world, pos, moved);
	}

	private static void detonateFirework(ServerWorld world, BlockPos pos) {
		try {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(0, List.of(
					new FireworkExplosionComponent(
							FireworkExplosionComponent.Type.LARGE_BALL,
							IntList.of(0xE0E0E0, 0x8B00FF, 0xF7C948),
							IntList.of(0x2B2B2B),
							true, true))));

			FireworkRocketEntity entity = new FireworkRocketEntity(world,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rocket);
			world.spawnEntity(entity);
		} catch (Throwable t) {
			InstantLore.LOGGER.warn("Firework on block break failed", t);
		}
	}
}
