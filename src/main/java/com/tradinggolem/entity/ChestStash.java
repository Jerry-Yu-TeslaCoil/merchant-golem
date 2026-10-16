package com.tradinggolem.entity;

import com.tradinggolem.block.TradingChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Reads and writes the golem's own trading chest. */
public final class ChestStash {
	private ChestStash() {
	}

	public static Container get(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TradingChestBlock)) {
			return null;
		}
		if (level.getBlockEntity(pos) instanceof Container container) {
			return container;
		}
		return null;
	}

	public static boolean isChest(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof TradingChestBlock;
	}

	public static ItemStack insert(Container container, ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty()) {
				int move = Math.min(stack.getCount(), stack.getMaxStackSize());
				container.setItem(slot, stack.split(move));
				continue;
			}
			if (!ItemStack.isSameItemSameComponents(existing, stack)) {
				continue;
			}
			int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing)) - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int move = Math.min(room, stack.getCount());
			existing.grow(move);
			stack.shrink(move);
			container.setItem(slot, existing);
		}
		container.setChanged();
		return stack;
	}

	public static int count(Container container, Item item) {
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static ItemStack extract(Container container, Item item, int amount) {
		ItemStack taken = ItemStack.EMPTY;
		for (int slot = 0; slot < container.getContainerSize() && (taken.isEmpty() || taken.getCount() < amount); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.is(item)) {
				continue;
			}
			int move = Math.min(amount - taken.getCount(), stack.getCount());
			if (taken.isEmpty()) {
				taken = stack.split(move);
			} else {
				stack.shrink(move);
				taken.grow(move);
			}
			container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}
		if (!taken.isEmpty()) {
			container.setChanged();
		}
		return taken;
	}

	public static ItemStack extractMatching(Container container, Predicate<ItemStack> predicate) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !predicate.test(stack)) {
				continue;
			}
			ItemStack taken = stack.copy();
			container.setItem(slot, ItemStack.EMPTY);
			container.setChanged();
			return taken;
		}
		return ItemStack.EMPTY;
	}

	/** Slot indexes of goods the golem is allowed to sell, in chest order. Emeralds stay put. */
	public static List<Integer> sellableSlots(Container container) {
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && !stack.is(Items.EMERALD)) {
				slots.add(slot);
			}
		}
		return slots;
	}
}
