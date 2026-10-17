package com.tradinggolem.entity;

import com.tradinggolem.block.SoldierChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

/** Builds the camp, then repairs it only while the stored order is rest. */
public final class SoldierCampGoal extends Goal {
	private static final int PAUSE = 20;
	private static final double REACH = 4.0 * 4.0;

	private final Soldier soldier;
	private int pause;
	private int stuck;
	@Nullable
	private BlockPos walkingTo;

	public SoldierCampGoal(Soldier soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return CampSite.shouldBuild(this.soldier);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.soldier.closeChest();
		this.soldier.getNavigation().stop();
		this.walkingTo = null;
		this.stuck = 0;
	}

	@Override
	public void tick() {
		if (!(this.soldier.level() instanceof ServerLevel level) || this.soldier.chestPos() == null) {
			return;
		}
		if (this.soldier.isSleeping()) {
			this.soldier.stopSleeping();
		}
		BlockPos chest = this.soldier.chestPos();
		if (chest != null && level.isLoaded(chest)) {
			this.parkMaterials(level, chest);
		}
		if (this.pause > 0) {
			this.pause--;
			return;
		}
		if (chest == null || !level.isLoaded(chest) || !(level.getBlockEntity(chest) instanceof SoldierChestBlockEntity block)) {
			return;
		}
		if (CampSite.isComplete(level, chest)) {
			if (!block.campCompleted()) {
				block.setCampCompleted(true);
			}
			if (block.order() != SoldierOrder.REST) {
				block.setOrder(SoldierOrder.REST);
			}
			this.soldier.closeChest();
			return;
		}
		CampSite.Work work = CampSite.next(level, chest, this.soldier);
		if (work == null) {
			this.idle(chest);
			return;
		}
		if (!this.prepare(level, chest, work)) {
			return;
		}
		if (!this.closeEnough(work.pos())) {
			this.walkTo(level, work.pos());
			return;
		}
		this.soldier.getNavigation().stop();
		this.stuck = 0;
		this.walkingTo = null;
		this.soldier.getLookControl().setLookAt(work.pos().getX() + 0.5, work.pos().getY() + 0.5, work.pos().getZ() + 0.5, 30.0F, 30.0F);
		if (this.perform(level, chest, work)) {
			this.soldier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			this.pause = PAUSE;
		}
	}

	private boolean prepare(ServerLevel level, BlockPos chest, CampSite.Work work) {
		if (work.kind() != CampSite.Work.Kind.CLEAR) {
			return true;
		}
		Predicate<ItemStack> needed = this.needed(level, work);
		if (needed == null || needed.test(this.soldier.getMainHandItem())) {
			return true;
		}
		if (this.takeFromBody(needed)) {
			return true;
		}
		if (!this.near(chest)) {
			this.walkTo(level, chest);
			return false;
		}
		Container container = level.getBlockEntity(chest) instanceof Container found ? found : null;
		if (container == null) {
			return false;
		}
		this.soldier.openChest(container, chest);
		this.soldier.getNavigation().stop();
		this.stashHand(container);
		ItemStack taken = ChestStash.extractMatching(container, needed);
		if (taken.isEmpty()) {
			this.soldier.closeChest();
			return false;
		}
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, taken);
		return true;
	}

	@Nullable
	private Predicate<ItemStack> needed(ServerLevel level, CampSite.Work work) {
		return switch (work.kind()) {
			case CLEAR -> {
				BlockState state = level.getBlockState(work.pos());
				if (CampSite.needsPick(state)) {
					yield stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(state);
				}
				if (CampSite.needsShovel(state)) {
					yield stack -> stack.is(ItemTags.SHOVELS) && stack.isCorrectToolForDrops(state);
				}
				yield null;
			}
			case STONE -> stack -> stack.is(Items.STONE);
			case BED -> stack -> stack.is(ItemTags.BEDS);
			case FENCE -> stack -> stack.is(ItemTags.WOODEN_FENCES);
		};
	}

	private boolean takeFromBody(Predicate<ItemStack> needed) {
		SimpleContainer inventory = this.soldier.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (!needed.test(inventory.getItem(slot))) {
				continue;
			}
			this.swap(inventory, slot);
			return true;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot == EquipmentSlot.MAINHAND || !needed.test(this.soldier.getItemBySlot(slot))) {
				continue;
			}
			ItemStack hand = this.soldier.getMainHandItem();
			ItemStack found = this.soldier.getItemBySlot(slot);
			this.soldier.setItemSlot(EquipmentSlot.MAINHAND, found);
			this.soldier.setItemSlot(slot, hand);
			return true;
		}
		return false;
	}

	private void swap(Container container, int slot) {
		ItemStack hand = this.soldier.getMainHandItem();
		ItemStack found = container.getItem(slot);
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, found);
		container.setItem(slot, hand);
	}

	private void stashHand(Container chest) {
		ItemStack hand = this.soldier.getMainHandItem();
		if (hand.isEmpty()) {
			return;
		}
		ItemStack leftover = ChestStash.insert(chest, hand.copy());
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, leftover);
	}

	private boolean perform(ServerLevel level, BlockPos chest, CampSite.Work work) {
		return switch (work.kind()) {
			case CLEAR -> this.clear(level, chest, work.pos());
			case STONE -> this.placeStone(level, chest, work.pos());
			case BED -> this.placeBed(level, chest);
			case FENCE -> this.placeFence(level, chest);
		};
	}

	private boolean clear(ServerLevel level, BlockPos chest, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || pos.equals(chest)) {
			return false;
		}
		ItemStack tool = this.soldier.getMainHandItem();
		if (CampSite.needsPick(state) && !tool.is(ItemTags.PICKAXES)) {
			return false;
		}
		if (CampSite.needsShovel(state) && !tool.is(ItemTags.SHOVELS)) {
			return false;
		}
		this.breakBlock(level, chest, pos, tool);
		if (tool.isDamageableItem()) {
			tool.hurtAndBreak(1, this.soldier, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	private void breakBlock(ServerLevel level, BlockPos chestPos, BlockPos pos, ItemStack tool) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		Container chest = level.getBlockEntity(chestPos) instanceof Container found ? found : null;
		if (level.getBlockEntity(pos) instanceof Container other && other != chest) {
			for (int slot = 0; slot < other.getContainerSize(); slot++) {
				this.give(level, chest, other.getItem(slot).copy());
				other.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (chest != null) {
			for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.soldier, tool)) {
				this.give(level, chest, drop.copy());
			}
		}
		BlockPos partner = null;
		if (state.hasProperty(DoublePlantBlock.HALF)) {
			partner = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		level.levelEvent(this.soldier, 2001, pos, Block.getId(state));
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		if (partner != null && level.getBlockState(partner).is(state.getBlock())) {
			level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private void give(ServerLevel level, @Nullable Container chest, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack leftover = chest == null ? stack : ChestStash.insert(chest, stack);
		if (!leftover.isEmpty()) {
			this.soldier.spawnAtLocation(level, leftover);
		}
	}

	private boolean placeStone(ServerLevel level, BlockPos chest, BlockPos pos) {
		if (!CampSite.canReceiveStone(level.getBlockState(pos)) || !this.consumeOne(level, chest, stack -> stack.is(Items.STONE))) {
			return false;
		}
		level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	private boolean placeBed(ServerLevel level, BlockPos chest) {
		BlockPos head = CampSite.bedHead(chest);
		BlockPos foot = CampSite.bedFoot(chest);
		ItemStack bed = this.findOne(level, chest, stack -> stack.is(ItemTags.BEDS));
		Block block = Block.byItem(bed.getItem());
		if (!(block instanceof BedBlock) || !level.getBlockState(head).isAir() || !level.getBlockState(foot).isAir()) {
			return false;
		}
		if (!this.consumeOne(level, chest, stack -> stack.is(bed.getItem()))) {
			return false;
		}
		BlockState state = block.defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
		level.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
		level.playSound(null, foot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	private boolean placeFence(ServerLevel level, BlockPos chest) {
		BlockPos pos = CampSite.hitch(chest);
		ItemStack fence = this.findOne(level, chest, stack -> stack.is(ItemTags.WOODEN_FENCES));
		Block block = Block.byItem(fence.getItem());
		if (fence.isEmpty() || !level.getBlockState(pos).isAir() || !block.defaultBlockState().is(BlockTags.WOODEN_FENCES)) {
			return false;
		}
		if (!this.consumeOne(level, chest, stack -> stack.is(fence.getItem()))) {
			return false;
		}
		level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	/** Stone, beds, and fences stay in the chest. The main hand does not keep the stack. */
	private void parkMaterials(ServerLevel level, BlockPos chestPos) {
		ItemStack hand = this.soldier.getMainHandItem();
		if (!this.isMaterial(hand)) {
			return;
		}
		ItemStack moving = hand.copy();
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Container chest = level.getBlockEntity(chestPos) instanceof Container found ? found : null;
		ItemStack leftover = chest == null ? moving : ChestStash.insert(chest, moving);
		leftover = this.soldier.getInventory().addItem(leftover);
		if (!leftover.isEmpty()) {
			this.soldier.spawnAtLocation(level, leftover);
		}
	}

	private boolean isMaterial(ItemStack stack) {
		return stack.is(Items.STONE) || stack.is(ItemTags.BEDS) || stack.is(ItemTags.WOODEN_FENCES);
	}

	private ItemStack findOne(ServerLevel level, BlockPos chestPos, Predicate<ItemStack> test) {
		Container chest = level.getBlockEntity(chestPos) instanceof Container found ? found : null;
		ItemStack foundStack = this.findIn(chest, test);
		if (!foundStack.isEmpty()) {
			return foundStack;
		}
		foundStack = this.findIn(this.soldier.getInventory(), test);
		if (!foundStack.isEmpty()) {
			return foundStack;
		}
		ItemStack hand = this.soldier.getMainHandItem();
		return test.test(hand) ? hand : ItemStack.EMPTY;
	}

	private ItemStack findIn(@Nullable Container container, Predicate<ItemStack> test) {
		if (container == null) {
			return ItemStack.EMPTY;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (test.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private boolean consumeOne(ServerLevel level, BlockPos chestPos, Predicate<ItemStack> test) {
		Container chest = level.getBlockEntity(chestPos) instanceof Container found ? found : null;
		if (this.takeOne(chest, test)) {
			this.soldier.openChest(chest, chestPos);
			return true;
		}
		if (this.takeOne(this.soldier.getInventory(), test)) {
			return true;
		}
		ItemStack hand = this.soldier.getMainHandItem();
		if (!test.test(hand)) {
			return false;
		}
		hand.shrink(1);
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, hand.isEmpty() ? ItemStack.EMPTY : hand);
		return true;
	}

	private boolean takeOne(@Nullable Container container, Predicate<ItemStack> test) {
		if (container == null) {
			return false;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!test.test(stack)) {
				continue;
			}
			stack.shrink(1);
			if (stack.isEmpty()) {
				container.setItem(slot, ItemStack.EMPTY);
			}
			container.setChanged();
			return true;
		}
		return false;
	}

	private void idle(BlockPos chest) {
		if (!this.near(chest)) {
			this.walkTo(this.soldier.level() instanceof ServerLevel level ? level : null, chest);
			return;
		}
		this.soldier.getNavigation().stop();
		this.soldier.closeChest();
	}

	private void walkTo(@Nullable ServerLevel level, BlockPos pos) {
		if (level == null) {
			return;
		}
		BlockPos feet = this.feet(level, pos);
		if (feet.equals(this.walkingTo) && this.soldier.tickCount % 15 != 0 && !this.soldier.getNavigation().isDone()) {
			if (this.soldier.getNavigation().isStuck() && ++this.stuck > 40) {
				this.stuck = 0;
				this.walkingTo = null;
				this.soldier.getNavigation().stop();
			}
			return;
		}
		boolean started = this.soldier.getNavigation().moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0);
		if (!started) {
			this.soldier.getMoveControl().setWantedPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0);
		}
		this.walkingTo = feet;
		this.stuck = 0;
		this.soldier.closeChest();
	}

	private BlockPos feet(ServerLevel level, BlockPos work) {
		BlockPos chest = this.soldier.chestPos();
		int ground = chest == null ? work.getY() : chest.getY();
		if (work.getY() < ground) {
			BlockPos bank = new BlockPos(work.getX(), ground, work.getZ());
			if (this.standable(level, bank)) {
				return bank;
			}
		}
		BlockPos above = work.above();
		if (this.standable(level, above)) {
			return above;
		}
		if (this.standable(level, work)) {
			return work;
		}
		return above;
	}

	private boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	private boolean closeEnough(BlockPos work) {
		double dx = this.soldier.getX() - (work.getX() + 0.5);
		double dz = this.soldier.getZ() - (work.getZ() + 0.5);
		return dx * dx + dz * dz <= REACH && Math.abs(this.soldier.getY() - work.getY()) <= 3.0;
	}

	private boolean near(BlockPos chest) {
		double dx = this.soldier.getX() - (chest.getX() + 0.5);
		double dz = this.soldier.getZ() - (chest.getZ() + 0.5);
		return dx * dx + dz * dz <= 9.0 && Math.abs(this.soldier.getY() - chest.getY()) <= 2.0;
	}
}
