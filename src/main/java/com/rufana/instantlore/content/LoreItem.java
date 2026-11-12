package com.rufana.instantlore.content;

import com.rufana.instantlore.server.LoreItemBus;
import com.rufana.instantlore.server.SoulMoney;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Consumer;

public class LoreItem extends Item {
	private final int slot;
	private final ComponentMap components;

	public LoreItem(int slot, Settings settings) {
		super(settings);
		this.slot = slot;
		this.components = new LoreItemComponents(slot, super.getComponents());
	}

	public int slot() {
		return slot;
	}

	public LoreItemDefinition definition() {
		return LoreContent.item(slot);
	}

	@Override
	public ComponentMap getComponents() {
		return components;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.literal(definition().displayName());
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		if (!definition().purse() || !LocalSoulBalance.known()) {
			return;
		}
		long balance = LocalSoulBalance.get();
		textConsumer.accept(Text.literal("На счету: " + balance + " " + souls(balance))
				.formatted(Formatting.GRAY));
	}

	private static String souls(long n) {
		long tail = Math.abs(n) % 100;
		long last = tail % 10;
		if (tail >= 11 && tail <= 14) {
			return "душ";
		}
		if (last == 1) {
			return "душа";
		}
		if (last >= 2 && last <= 4) {
			return "души";
		}
		return "душ";
	}

	@Override
	public ActionResult use(World world, PlayerEntity player, Hand hand) {
		if (!definition().usable()) {
			return ActionResult.PASS;
		}

		if (definition().consumable()) {
			player.setCurrentHand(hand);
			return ActionResult.CONSUME;
		}

		if (player instanceof ServerPlayerEntity serverPlayer) {
			LoreItemBus.used(serverPlayer, slot, definition(), hand, null);
		}

		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!definition().usable()) {
			return ActionResult.PASS;
		}

		if (definition().consumable()) {
			return ActionResult.PASS;
		}

		if (context.getPlayer() instanceof ServerPlayerEntity serverPlayer) {
			BlockPos pos = context.getBlockPos();
			LoreItemBus.used(serverPlayer, slot, definition(), context.getHand(), pos);
		}

		return ActionResult.SUCCESS;
	}

	@Override
	public boolean onClicked(ItemStack stack, ItemStack cursorStack, Slot slot, ClickType clickType,
			PlayerEntity player, StackReference cursorStackReference) {
		if (!definition().purse()) {
			return false;
		}

		int coinOnCursor = coinValue(cursorStack);
		boolean deposit = coinOnCursor > 0;
		boolean withdraw = cursorStack.isEmpty() && clickType == ClickType.RIGHT;

		if (!deposit && !withdraw) {
			return false;
		}

		if (!(player instanceof ServerPlayerEntity server)) {
			return true;
		}

		java.util.UUID uuid = server.getUuid();

		if (deposit) {
			int count = clickType == ClickType.LEFT ? cursorStack.getCount() : 1;
			SoulMoney.deposit(uuid, (long) coinOnCursor * count);
			ItemStack left = cursorStack.copy();
			left.decrement(count);
			cursorStackReference.set(left.isEmpty() ? ItemStack.EMPTY : left);
			SoulMoney.syncTo(server);
			return true;
		}

		// withdraw
		long balance = SoulMoney.get(uuid);
		Coins.Denom denom = Coins.highestNotExceeding(balance);
		if (denom == null) {
			server.sendMessage(Text.literal("Кошелёк пуст."), true);
			return true;
		}
		if (SoulMoney.withdraw(uuid, denom.value())) {
			cursorStackReference.set(new ItemStack(LoreItemPool.get(denom.slot()), 1));
			SoulMoney.syncTo(server);
		}
		return true;
	}

	private static int coinValue(ItemStack stack) {
		if (stack.getItem() instanceof LoreItem item) {
			LoreItemDefinition def = item.definition();
			return def.isCoin() ? def.coin() : 0;
		}
		return 0;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		if (!definition().consumable()) {
			return UseAction.NONE;
		}
		return "eat".equalsIgnoreCase(definition().useAction()) ? UseAction.EAT : UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return definition().consumable() ? definition().useTicks() : 0;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!definition().consumable()) {
			return super.finishUsing(stack, world, user);
		}

		stack.decrementUnlessCreative(1, user);

		if (user instanceof ServerPlayerEntity serverPlayer) {
			LoreItemBus.used(serverPlayer, slot, definition(), user.getActiveHand(), null);
		}

		return stack;
	}
}
