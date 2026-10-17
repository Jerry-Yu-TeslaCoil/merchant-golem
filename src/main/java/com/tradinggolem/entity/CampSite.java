package com.tradinggolem.entity;

import com.tradinggolem.block.SoldierChestBlock;
import com.tradinggolem.block.SoldierChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.function.Predicate;

/** The 7×7 camp under a soldier chest, plus the demand list for what is still missing. */
public final class CampSite {
	public static final int RADIUS = 3;

	private CampSite() {
	}

	public static boolean shouldBuild(Soldier soldier) {
		if (!(soldier.level() instanceof ServerLevel level) || soldier.chestPos() == null || !level.isLoaded(soldier.chestPos())) {
			return false;
		}
		if (!(level.getBlockEntity(soldier.chestPos()) instanceof SoldierChestBlockEntity chest)) {
			return false;
		}
		if (!chest.campCompleted()) {
			return true;
		}
		if (soldier.alerting()) {
			return false;
		}
		return chest.order() == SoldierOrder.REST && !isComplete(level, soldier.chestPos());
	}

	public static boolean isComplete(ServerLevel level, BlockPos chest) {
		if (!bedReady(level, chest) || !fenceReady(level, chest)) {
			return false;
		}
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockPos pos = chest.offset(dx, dy, dz);
					if (!isDesired(level, chest, pos, level.getBlockState(pos))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static String demands(ServerLevel level, BlockPos chest, Soldier soldier) {
		Container container = level.getBlockEntity(chest) instanceof Container found ? found : null;
		boolean needsPick = false;
		boolean needsShovel = false;
		int placedStone = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockPos pos = chest.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (dy == -1 && state.is(Blocks.STONE)) {
						placedStone++;
					}
					if (isDesired(level, chest, pos, state) || state.isAir() || isFluid(state) || state.getDestroySpeed(level, pos) < 0.0F) {
						continue;
					}
					if (needsPick(state)) {
						needsPick = true;
					} else if (needsShovel(state)) {
						needsShovel = true;
					}
				}
			}
		}
		StringBuilder text = new StringBuilder();
		if (needsPick && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.PICKAXES))) {
			append(text, "PICK");
		}
		if (needsShovel && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.SHOVELS))) {
			append(text, "SHOVEL");
		}
		int stone = 49 - placedStone - count(container, soldier, stack -> stack.is(Items.STONE));
		if (stone > 0) {
			append(text, "STONE:" + stone);
		}
		if (!bedReady(level, chest) && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.BEDS))) {
			append(text, "BED");
		}
		if (!fenceReady(level, chest) && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.WOODEN_FENCES))) {
			append(text, "FENCE");
		}
		int food = count(container, soldier, CampSite::edible);
		if (food < 3) {
			append(text, "FOOD:" + (16 - food));
		}
		return text.toString();
	}

	public static Work next(ServerLevel level, BlockPos chest, Soldier soldier) {
		Container container = level.getBlockEntity(chest) instanceof Container found ? found : null;
		Work clear = nearest(level, chest, pos -> pos.getY() >= chest.getY() && needsClear(level, chest, pos, level.getBlockState(pos), container, soldier));
		if (clear != null) {
			return clear.as(Work.Kind.CLEAR);
		}
		if (has(level, chest, container, soldier, stack -> stack.is(Items.STONE))) {
			Work stone = nearest(level, chest, pos -> pos.getY() == chest.getY() - 1 && canReceiveStone(level.getBlockState(pos)));
			if (stone != null) {
				return stone.as(Work.Kind.STONE);
			}
			Work dig = nearest(level, chest, pos -> pos.getY() == chest.getY() - 1 && needsClear(level, chest, pos, level.getBlockState(pos), container, soldier));
			if (dig != null) {
				return dig.as(Work.Kind.CLEAR);
			}
		}
		if (!bedReady(level, chest) && bedSpotsOpen(level, chest) && has(level, chest, container, soldier, stack -> stack.is(ItemTags.BEDS))) {
			return new Work(Work.Kind.BED, bedHead(chest));
		}
		if (!fenceReady(level, chest) && level.getBlockState(hitch(chest)).isAir() && has(level, chest, container, soldier, stack -> stack.is(ItemTags.WOODEN_FENCES))) {
			return new Work(Work.Kind.FENCE, hitch(chest));
		}
		return null;
	}

	public static boolean needsPick(BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.requiresCorrectToolForDrops();
	}

	public static boolean needsShovel(BlockState state) {
		return state.is(BlockTags.MINEABLE_WITH_SHOVEL) && state.requiresCorrectToolForDrops();
	}

	public static boolean edible(ItemStack stack) {
		return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
	}

	public static BlockPos bedHead(BlockPos chest) {
		return chest.offset(-2, 0, -2);
	}

	public static BlockPos bedFoot(BlockPos chest) {
		return chest.offset(-1, 0, -2);
	}

	public static BlockPos hitch(BlockPos chest) {
		return chest.offset(2, 0, 2);
	}

	private static Work nearest(ServerLevel level, BlockPos chest, Predicate<BlockPos> test) {
		Work best = null;
		double distance = Double.MAX_VALUE;
		for (int dy = 1; dy >= -1; dy--) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					BlockPos pos = chest.offset(dx, dy, dz);
					if (!level.isLoaded(pos) || !test.test(pos)) {
						continue;
					}
					double here = pos.distSqr(chest);
					if (best == null || dy > best.pos().getY() - chest.getY() || (dy == best.pos().getY() - chest.getY() && here < distance)) {
						best = new Work(Work.Kind.CLEAR, pos.immutable());
						distance = here;
					}
				}
			}
			if (best != null && best.pos().getY() - chest.getY() == dy) {
				return best;
			}
		}
		return best;
	}

	private static boolean needsClear(ServerLevel level, BlockPos chest, BlockPos pos, BlockState state, Container container, Soldier soldier) {
		if (isDesired(level, chest, pos, state) || state.isAir()) {
			return false;
		}
		if (pos.getY() == chest.getY() - 1 && canReceiveStone(state)) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F && !isFluid(state)) {
			return false;
		}
		if (needsPick(state) && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.PICKAXES))) {
			return false;
		}
		if (needsShovel(state) && !has(level, chest, container, soldier, stack -> stack.is(ItemTags.SHOVELS))) {
			return false;
		}
		return true;
	}

	private static boolean isDesired(ServerLevel level, BlockPos chest, BlockPos pos, BlockState state) {
		if (pos.equals(chest)) {
			return state.getBlock() instanceof SoldierChestBlock;
		}
		if (pos.getY() == chest.getY() - 1) {
			return state.is(Blocks.STONE);
		}
		if (pos.equals(bedHead(chest))) {
			return isBedPart(state, BedPart.HEAD);
		}
		if (pos.equals(bedFoot(chest))) {
			return isBedPart(state, BedPart.FOOT);
		}
		if (pos.equals(hitch(chest))) {
			return state.is(BlockTags.WOODEN_FENCES);
		}
		return state.isAir() && state.getFluidState().isEmpty();
	}

	private static boolean isBedPart(BlockState state, BedPart part) {
		return state.getBlock() instanceof BedBlock
				&& state.getValue(BedBlock.PART) == part
				&& state.getValue(BedBlock.FACING) == Direction.WEST;
	}

	private static boolean bedReady(ServerLevel level, BlockPos chest) {
		return isBedPart(level.getBlockState(bedHead(chest)), BedPart.HEAD)
				&& isBedPart(level.getBlockState(bedFoot(chest)), BedPart.FOOT);
	}

	private static boolean fenceReady(ServerLevel level, BlockPos chest) {
		return level.getBlockState(hitch(chest)).is(BlockTags.WOODEN_FENCES);
	}

	private static boolean bedSpotsOpen(ServerLevel level, BlockPos chest) {
		return level.getBlockState(bedHead(chest)).isAir() && level.getBlockState(bedFoot(chest)).isAir();
	}

	public static boolean canReceiveStone(BlockState state) {
		return state.isAir() || isFluid(state);
	}

	private static boolean isFluid(BlockState state) {
		return state.is(Blocks.WATER) || state.is(Blocks.LAVA) || state.is(Blocks.BUBBLE_COLUMN);
	}

	private static boolean has(ServerLevel level, BlockPos chest, Container container, Soldier soldier, Predicate<ItemStack> test) {
		return count(container, soldier, test) > 0;
	}

	private static int count(Container container, Soldier soldier, Predicate<ItemStack> test) {
		int total = count(container, test);
		total += count(soldier.getInventory(), test);
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			ItemStack stack = soldier.getItemBySlot(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int count(Container container, Predicate<ItemStack> test) {
		if (container == null) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void append(StringBuilder text, String part) {
		if (!text.isEmpty()) {
			text.append(',');
		}
		text.append(part);
	}

	public record Work(Kind kind, BlockPos pos) {
		public Work as(Kind kind) {
			return new Work(kind, this.pos);
		}

		public enum Kind {
			CLEAR,
			STONE,
			BED,
			FENCE
		}
	}
}
