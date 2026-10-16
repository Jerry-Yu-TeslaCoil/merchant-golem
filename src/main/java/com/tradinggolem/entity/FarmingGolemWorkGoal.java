package com.tradinggolem.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the ditch as a ring, pours water into the four corners, tills the inner field,
 * plants a random crop from the chest, and brings ripe crops back.
 */
public final class FarmingGolemWorkGoal extends Goal {
	private static final int ACTION_PAUSE = 8;
	private static final int FLOW_WAIT = 40;
	private static final int SUPPLY_WAIT = 80;
	private static final double REACH_SQUARED = 4.0 * 4.0;

	private final FarmingGolem golem;
	private final Map<BlockPos, Integer> skipUntil = new HashMap<>();
	private int ringCursor;
	private int actionPause;
	private int stuckTicks;
	@Nullable
	private BlockPos walkingTo;

	public FarmingGolemWorkGoal(FarmingGolem golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return true;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.golem.cooldown > 0) {
			this.golem.cooldown--;
		}
		if (this.actionPause > 0) {
			this.actionPause--;
		}

		this.ensureChest(level);
		BlockPos chest = this.golem.chestPos;
		if (chest == null || !CropStash.isChest(level, chest)) {
			this.golem.getNavigation().stop();
			return;
		}

		if (this.golem.flowCooldown > 0) {
			this.golem.flowCooldown--;
			Task harvest = this.findHarvest(level, chest);
			if (harvest != null && this.golem.carried.isEmpty() && this.golem.rewards.size() < 8 && this.actionPause == 0) {
				this.work(level, chest, harvest);
				return;
			}
			if (this.shouldStore(null)) {
				this.returnHome(level, null);
				return;
			}
			this.idleNearChest();
			return;
		}

		Task task = this.findTask(level, chest);
		if (this.shouldVisitChest(task)) {
			this.returnHome(level, task);
			return;
		}
		if (task == null || (this.golem.carried.isEmpty() && this.needsSupply(task))) {
			this.idleNearChest();
			return;
		}
		if (this.actionPause > 0) {
			this.lookAt(task.pos);
			this.golem.getNavigation().stop();
			return;
		}
		this.work(level, chest, task);
	}

	private boolean shouldVisitChest(@Nullable Task task) {
		if (this.shouldStore(task)) {
			return true;
		}
		return this.golem.carried.isEmpty() && this.needsSupply(task) && this.golem.cooldown == 0;
	}

	private boolean shouldStore(@Nullable Task task) {
		if (!this.golem.carried.isEmpty() && !this.useful(this.golem.carried, task)) {
			return true;
		}
		if (this.golem.rewards.size() >= 8) {
			return true;
		}
		return !this.golem.rewards.isEmpty() && task == null;
	}

	private boolean needsSupply(@Nullable Task task) {
		return task != null && task.kind != TaskKind.HARVEST;
	}

	private boolean useful(ItemStack stack, @Nullable Task task) {
		if (task == null || stack.isEmpty()) {
			return false;
		}
		return switch (task.kind) {
			case DIG -> this.golem.level() instanceof ServerLevel server && this.canUseTool(stack, server.getBlockState(task.pos));
			case FILL -> stack.is(Items.DIRT);
			case WATER -> stack.is(Items.WATER_BUCKET);
			case TILL -> stack.is(ItemTags.HOES);
			case PLANT -> FarmCrops.isSeed(stack.getItem());
			case HARVEST -> false;
		};
	}

	private void work(ServerLevel level, BlockPos chest, Task task) {
		this.golem.closeChest();
		if (!this.closeEnough(task.pos)) {
			this.walkTo(level, chest, task.pos);
			return;
		}
		this.golem.getNavigation().stop();
		this.stuckTicks = 0;
		this.walkingTo = null;
		this.lookAt(task.pos);
		boolean done = switch (task.kind) {
			case DIG -> this.dig(level, task.pos);
			case FILL -> this.fill(level, task.pos);
			case WATER -> this.pour(level, task.pos);
			case TILL -> this.till(level, task.pos);
			case PLANT -> this.plant(level, task.pos);
			case HARVEST -> this.harvest(level, task.pos);
		};
		if (done) {
			this.golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			this.actionPause = ACTION_PAUSE;
		}
	}

	private boolean dig(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!this.canUseTool(this.golem.carried, state) || !this.isDigTarget(level, pos, state)) {
			return false;
		}
		BlockPos chest = this.golem.chestPos;
		if (chest != null && pos.getY() == FarmLayout.ground(chest).getY()) {
			BlockPos above = pos.above();
			BlockState cover = level.getBlockState(above);
			if (this.isClearableCover(level, above, cover) && !this.isKeptPlant(cover)) {
				this.breakBlock(level, above, this.golem.carried);
			}
			state = level.getBlockState(pos);
			if (!this.canUseTool(this.golem.carried, state) || !this.isDigTarget(level, pos, state)) {
				return false;
			}
		}
		this.breakBlock(level, pos, this.golem.carried);
		this.golem.wearDownTool();
		return true;
	}

	private boolean fill(ServerLevel level, BlockPos pos) {
		if (!this.golem.carried.is(Items.DIRT) || !this.needsDirt(level, pos)) {
			return false;
		}
		level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		this.golem.carried.shrink(1);
		this.golem.setCarried(this.golem.carried.isEmpty() ? ItemStack.EMPTY : this.golem.carried);
		return true;
	}

	private boolean pour(ServerLevel level, BlockPos pos) {
		if (!this.golem.carried.is(Items.WATER_BUCKET)) {
			return false;
		}
		if (!this.needsWater(level, pos)) {
			return false;
		}
		level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		if (!this.isWater(level.getBlockState(pos))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			this.skipUntil.put(pos, this.golem.tickCount + 200);
			return false;
		}
		level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
		this.golem.setCarried(new ItemStack(Items.BUCKET));
		this.golem.flowCooldown = FLOW_WAIT;
		return true;
	}

	private boolean till(ServerLevel level, BlockPos pos) {
		if (!this.golem.carried.is(ItemTags.HOES)) {
			return false;
		}
		BlockPos above = pos.above();
		BlockState cover = level.getBlockState(above);
		if (this.isClearableCover(level, above, cover)) {
			this.breakBlock(level, above, this.golem.carried);
		}
		BlockState state = level.getBlockState(pos);
		if (!FarmCrops.isTillable(state) || !level.getBlockState(above).isAir()) {
			return false;
		}
		BlockState tilled = state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
				? Blocks.DIRT.defaultBlockState()
				: Blocks.FARMLAND.defaultBlockState();
		if (state.is(Blocks.ROOTED_DIRT)) {
			this.golem.addReward(new ItemStack(Items.HANGING_ROOTS));
		}
		level.setBlock(pos, tilled, Block.UPDATE_ALL);
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		this.golem.wearDownTool();
		return true;
	}

	private boolean plant(ServerLevel level, BlockPos soil) {
		Item seed = this.golem.carried.getItem();
		Block crop = FarmCrops.cropFor(seed);
		if (crop == null) {
			return false;
		}
		BlockPos chest = this.golem.chestPos;
		if (chest != null && !this.seedFitsPlot(chest, soil, seed)) {
			return false;
		}
		BlockPos pos = soil.above();
		if (!level.getBlockState(soil).is(Blocks.FARMLAND) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		level.setBlock(pos, crop.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
		this.golem.carried.shrink(1);
		this.golem.setCarried(this.golem.carried.isEmpty() ? ItemStack.EMPTY : this.golem.carried);
		return true;
	}

	private boolean harvest(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!FarmCrops.isMature(state)) {
			return false;
		}
		this.breakBlock(level, pos, ItemStack.EMPTY);
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	private void breakBlock(ServerLevel level, BlockPos pos, ItemStack tool) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof Container container) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty()) {
					this.golem.addReward(stack.copy());
					container.setItem(slot, ItemStack.EMPTY);
				}
			}
			container.setChanged();
		}
		for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.golem, tool)) {
			this.golem.addReward(drop.copy());
		}
		BlockPos partner = null;
		if (state.hasProperty(DoublePlantBlock.HALF)) {
			partner = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		level.levelEvent(this.golem, 2001, pos, Block.getId(state));
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		if (partner != null && level.getBlockState(partner).is(state.getBlock())) {
			level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private boolean isDigTarget(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || this.isWater(state) || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}
		if (state.getBlock() instanceof BaseEntityBlock || this.isKeptPlant(state)) {
			return false;
		}
		return true;
	}

	private boolean isKeptPlant(BlockState state) {
		if (state.getBlock() instanceof CropBlock || FarmCrops.isMature(state)) {
			return true;
		}
		return state.is(Blocks.MELON_STEM)
				|| state.is(Blocks.PUMPKIN_STEM)
				|| state.is(Blocks.ATTACHED_MELON_STEM)
				|| state.is(Blocks.ATTACHED_PUMPKIN_STEM)
				|| state.is(Blocks.PITCHER_PLANT)
				|| state.is(Blocks.TORCHFLOWER);
	}

	/** 铲子挖土和不用特定工具的方块，镐子挖石头。 */
	private boolean canUseTool(ItemStack stack, BlockState state) {
		if (!stack.is(ItemTags.SHOVELS) && !stack.is(ItemTags.PICKAXES)) {
			return false;
		}
		if (state.requiresCorrectToolForDrops()) {
			return stack.isCorrectToolForDrops(state);
		}
		return true;
	}

	private boolean toolAvailable(ServerLevel level, BlockPos chest, BlockState state) {
		if (this.canUseTool(this.golem.carried, state)) {
			return true;
		}
		Container container = CropStash.get(level, chest);
		if (container == null) {
			return false;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (this.canUseTool(container.getItem(slot), state)) {
				return true;
			}
		}
		return false;
	}

	private boolean needsDirt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.DIRT) || state.is(Blocks.FARMLAND) || FarmCrops.isTillable(state)) {
			return false;
		}
		if (state.isAir() || this.isWater(state) || !state.getFluidState().isEmpty()) {
			return true;
		}
		return state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty() && !this.isKeptPlant(state);
	}

	private boolean isClearableCover(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.getBlock() instanceof BaseEntityBlock) {
			return false;
		}
		if (state.getBlock() instanceof CropBlock || FarmCrops.isMature(state)) {
			return true;
		}
		return state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty();
	}

	private boolean needsWater(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			return false;
		}
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	private boolean isWater(BlockState state) {
		return state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN);
	}

	@Nullable
	private Task findTask(ServerLevel level, BlockPos chest) {
		Task harvest = this.findHarvest(level, chest);
		if (harvest != null) {
			return harvest;
		}
		BlockPos dig = this.findDig(level, chest, true);
		if (dig == null) {
			dig = this.findDig(level, chest, false);
		}
		if (dig != null) {
			return new Task(TaskKind.DIG, dig);
		}
		BlockPos fill = this.findFill(level, chest);
		if (fill != null) {
			return new Task(TaskKind.FILL, fill);
		}
		BlockPos water = this.findDitch(level, chest, false);
		if (water != null) {
			return new Task(TaskKind.WATER, water);
		}
		BlockPos till = this.findPlot(level, chest, false);
		if (till != null) {
			return new Task(TaskKind.TILL, till);
		}
		BlockPos plant = this.findPlot(level, chest, true);
		if (plant != null) {
			return new Task(TaskKind.PLANT, plant);
		}
		return null;
	}

	@Nullable
	private Task findHarvest(ServerLevel level, BlockPos chest) {
		BlockPos[] found = new BlockPos[1];
		FarmLayout.forEachPlot(chest, plot -> {
			if (found[0] != null) {
				return;
			}
			BlockPos crop = plot.above();
			if (this.ready(crop) && FarmCrops.isMature(level.getBlockState(crop))) {
				found[0] = crop;
				return;
			}
			BlockPos taller = crop.above();
			if (this.ready(taller) && FarmCrops.isMature(level.getBlockState(taller))) {
				found[0] = taller;
			}
		});
		if (found[0] != null) {
			return new Task(TaskKind.HARVEST, found[0]);
		}
		FarmLayout.forEachDitch(chest, ditch -> {
			if (found[0] != null) {
				return;
			}
			BlockPos fruit = ditch.above();
			BlockState state = level.getBlockState(fruit);
			if (this.ready(fruit) && (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN))) {
				found[0] = fruit;
			}
		});
		return found[0] == null ? null : new Task(TaskKind.HARVEST, found[0]);
	}

	@Nullable
	private BlockPos findDig(ServerLevel level, BlockPos chest, boolean preferCarried) {
		for (int layer = 1; layer >= 0; layer--) {
			BlockPos headroom = this.nearestDig(level, chest, preferCarried, layer);
			if (headroom != null) {
				return headroom;
			}
		}
		BlockPos ditch = this.findDitch(level, chest, true, preferCarried);
		if (ditch != null) {
			return ditch;
		}
		return this.nearestPlotObstacle(level, chest, preferCarried);
	}

	@Nullable
	private BlockPos nearestDig(ServerLevel level, BlockPos chest, boolean preferCarried, int layer) {
		BlockPos[] found = new BlockPos[1];
		double[] best = {Double.MAX_VALUE};
		FarmLayout.forEachHeadroom(chest, layer, pos -> {
			if (!this.ready(pos) || !this.canTargetDig(level, chest, pos, preferCarried)) {
				return;
			}
			double distance = this.golem.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (distance < best[0]) {
				best[0] = distance;
				found[0] = pos;
			}
		});
		return found[0];
	}

	@Nullable
	private BlockPos nearestPlotObstacle(ServerLevel level, BlockPos chest, boolean preferCarried) {
		BlockPos[] found = new BlockPos[1];
		double[] best = {Double.MAX_VALUE};
		FarmLayout.forEachPlot(chest, plot -> {
			BlockState soil = level.getBlockState(plot);
			if (soil.is(Blocks.FARMLAND) || FarmCrops.isTillable(soil)) {
				return;
			}
			if (!this.ready(plot) || !this.canTargetDig(level, chest, plot, preferCarried)) {
				return;
			}
			double distance = this.golem.distanceToSqr(plot.getX() + 0.5, plot.getY(), plot.getZ() + 0.5);
			if (distance < best[0]) {
				best[0] = distance;
				found[0] = plot;
			}
		});
		return found[0];
	}

	private boolean canTargetDig(ServerLevel level, BlockPos chest, BlockPos pos, boolean preferCarried) {
		BlockState state = level.getBlockState(pos);
		if (!this.isDigTarget(level, pos, state)) {
			return false;
		}
		if (preferCarried) {
			return this.canUseTool(this.golem.carried, state);
		}
		return this.toolAvailable(level, chest, state);
	}

	@Nullable
	private BlockPos findFill(ServerLevel level, BlockPos chest) {
		BlockPos[] found = new BlockPos[1];
		double[] best = {Double.MAX_VALUE};
		java.util.function.Consumer<BlockPos> consider = pos -> {
			if (found[0] != null && this.golem.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) >= best[0]) {
				return;
			}
			if (!this.ready(pos) || !this.needsDirt(level, pos)) {
				return;
			}
			if (!this.golem.carried.is(Items.DIRT) && !this.chestHasDirt(level, chest)) {
				return;
			}
			double distance = this.golem.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (distance < best[0]) {
				best[0] = distance;
				found[0] = pos;
			}
		};
		FarmLayout.forEachDitch(chest, ditch -> consider.accept(ditch.below()));
		FarmLayout.forEachPlot(chest, consider);
		FarmLayout.forEachBridge(chest, consider);
		BlockPos support = FarmLayout.ground(chest);
		consider.accept(support);
		return found[0];
	}

	private boolean chestHasDirt(ServerLevel level, BlockPos chest) {
		Container container = CropStash.get(level, chest);
		if (container == null) {
			return false;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(Items.DIRT)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private BlockPos findDitch(ServerLevel level, BlockPos chest, boolean digging) {
		return this.findDitch(level, chest, digging, false);
	}

	@Nullable
	private BlockPos findDitch(ServerLevel level, BlockPos chest, boolean digging, boolean preferCarried) {
		if (!digging) {
			BlockPos[] found = new BlockPos[1];
			FarmLayout.forEachWaterCorner(chest, corner -> {
				if (found[0] == null && this.ready(corner) && this.needsWater(level, corner)) {
					found[0] = corner;
				}
			});
			return found[0];
		}
		List<BlockPos> ring = FarmLayout.ditchRing(chest);
		int size = ring.size();
		if (size == 0) {
			return null;
		}
		int start = Math.floorMod(this.ringCursor, size);
		for (int step = 0; step < size; step++) {
			int index = (start + step) % size;
			BlockPos pos = ring.get(index);
			if (!this.ready(pos)) {
				continue;
			}
			if (this.canTargetDig(level, chest, pos, preferCarried)) {
				this.ringCursor = index;
				return pos;
			}
		}
		return null;
	}

	@Nullable
	private BlockPos findPlot(ServerLevel level, BlockPos chest, boolean planting) {
		BlockPos[] found = new BlockPos[1];
		FarmLayout.forEachPlot(chest, plot -> {
			if (found[0] != null || !this.ready(plot)) {
				return;
			}
			BlockState soil = level.getBlockState(plot);
			BlockState above = level.getBlockState(plot.above());
			if (planting) {
				if (soil.is(Blocks.FARMLAND) && above.isAir() && this.canSupplyPlant(level, chest, plot)) {
					found[0] = plot;
				}
				return;
			}
			if (!FarmCrops.isTillable(soil)) {
				return;
			}
			if (above.isAir() || this.isClearableCover(level, plot.above(), above)) {
				found[0] = plot;
			}
		});
		return found[0];
	}

	private boolean canSupplyPlant(ServerLevel level, BlockPos chest, BlockPos plot) {
		Item carried = this.golem.carried.getItem();
		if (FarmCrops.isSeed(carried)) {
			return this.seedFitsPlot(chest, plot, carried);
		}
		if (!this.golem.carried.isEmpty()) {
			return false;
		}
		Container container = CropStash.get(level, chest);
		if (container == null) {
			return false;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			Item item = container.getItem(slot).getItem();
			if (FarmCrops.isSeed(item) && this.seedFitsPlot(chest, plot, item)) {
				return true;
			}
		}
		return false;
	}

	private boolean seedHasPlot(ServerLevel level, BlockPos chest, Item seed) {
		boolean[] found = new boolean[1];
		FarmLayout.forEachPlot(chest, plot -> {
			if (found[0] || !this.seedFitsPlot(chest, plot, seed)) {
				return;
			}
			if (level.getBlockState(plot).is(Blocks.FARMLAND) && level.getBlockState(plot.above()).isAir()) {
				found[0] = true;
			}
		});
		return found[0];
	}

	private boolean seedFitsPlot(BlockPos chest, BlockPos plot, Item seed) {
		if (seed == Items.MELON_SEEDS || seed == Items.PUMPKIN_SEEDS) {
			return FarmLayout.gourdStem(chest, plot);
		}
		return true;
	}

	private boolean ready(BlockPos pos) {
		Integer until = this.skipUntil.get(pos);
		return until == null || until <= this.golem.tickCount;
	}

	private void returnHome(ServerLevel level, @Nullable Task task) {
		if (!this.golem.nearChest()) {
			this.walkToChest();
			return;
		}
		Container container = CropStash.get(level, this.golem.chestPos);
		if (container == null) {
			return;
		}
		this.golem.openChest(container, this.golem.chestPos);
		this.golem.getNavigation().stop();
		if (!this.golem.carried.isEmpty() && !this.useful(this.golem.carried, task)) {
			this.deposit(level, container, this.golem.carried);
			this.golem.setCarried(ItemStack.EMPTY);
		}
		List<ItemStack> rewards = new ArrayList<>(this.golem.rewards);
		this.golem.rewards.clear();
		for (ItemStack reward : rewards) {
			this.deposit(level, container, reward);
		}
		if (this.golem.carried.isEmpty() && this.needsSupply(task) && this.golem.flowCooldown == 0) {
			ItemStack taken = this.takeSupply(container, task);
			if (taken.isEmpty()) {
				this.golem.closeChest();
				this.golem.cooldown = SUPPLY_WAIT;
				return;
			}
			this.golem.setCarried(taken);
			this.actionPause = 10;
		}
	}

	private ItemStack takeSupply(Container container, @Nullable Task task) {
		if (task == null) {
			return ItemStack.EMPTY;
		}
		return switch (task.kind) {
			case DIG -> this.takeDigTool(container, task);
			case FILL -> ChestStash.extract(container, Items.DIRT, 64);
			case WATER -> ChestStash.extractMatching(container, stack -> stack.is(Items.WATER_BUCKET));
			case TILL -> ChestStash.extractMatching(container, stack -> stack.is(ItemTags.HOES));
			case PLANT -> this.takeRandomSeed(container, task);
			case HARVEST -> ItemStack.EMPTY;
		};
	}

	private ItemStack takeDigTool(Container container, Task task) {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return ItemStack.EMPTY;
		}
		BlockState state = level.getBlockState(task.pos);
		if (state.requiresCorrectToolForDrops()) {
			return ChestStash.extractMatching(container, stack -> this.canUseTool(stack, state));
		}
		ItemStack shovel = ChestStash.extractMatching(container, stack -> stack.is(ItemTags.SHOVELS));
		if (!shovel.isEmpty()) {
			return shovel;
		}
		return ChestStash.extractMatching(container, stack -> stack.is(ItemTags.PICKAXES));
	}

	private ItemStack takeRandomSeed(Container container, Task task) {
		if (!(this.golem.level() instanceof ServerLevel level) || this.golem.chestPos == null) {
			return ItemStack.EMPTY;
		}
		BlockPos chest = this.golem.chestPos;
		List<Item> types = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			Item item = container.getItem(slot).getItem();
			if (!FarmCrops.isSeed(item) || types.contains(item) || !this.seedFitsPlot(chest, task.pos, item)) {
				continue;
			}
			if (this.seedHasPlot(level, chest, item)) {
				types.add(item);
			}
		}
		if (types.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Item pick = types.get(this.golem.getRandom().nextInt(types.size()));
		return ChestStash.extract(container, pick, 8);
	}

	private void deposit(ServerLevel level, Container container, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack leftover = ChestStash.insert(container, stack.copy());
		if (!leftover.isEmpty()) {
			this.golem.spawnAtLocation(level, leftover);
		}
	}

	private void ensureChest(ServerLevel level) {
		if (this.golem.chestPos != null || this.golem.tickCount % 40 != 0) {
			return;
		}
		AABB area = this.golem.getBoundingBox().inflate(12.0);
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(area.minX, area.minY, area.minZ),
				BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
			if (!CropStash.isChest(level, pos) || this.claimedByAnother(level, pos)) {
				continue;
			}
			double distance = this.golem.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = pos.immutable();
			}
		}
		if (nearest != null) {
			this.golem.bindChest(nearest);
		}
	}

	private boolean claimedByAnother(ServerLevel level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(64.0);
		for (FarmingGolem other : level.getEntitiesOfClass(FarmingGolem.class, area, entity -> entity != this.golem && entity.isAlive())) {
			if (pos.equals(other.chestPos())) {
				return true;
			}
		}
		return false;
	}

	private void idleNearChest() {
		if (!this.golem.nearChest()) {
			this.walkToChest();
			return;
		}
		this.golem.getNavigation().stop();
		this.golem.closeChest();
	}

	private void walkToChest() {
		BlockPos pos = this.golem.chestPos;
		if (pos == null) {
			return;
		}
		this.golem.closeChest();
		if (this.golem.tickCount % 15 != 0 && !this.golem.getNavigation().isDone()) {
			return;
		}
		BlockPos feet = pos.above();
		this.golem.getNavigation().moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0);
	}

	private void walkTo(ServerLevel level, BlockPos chest, BlockPos work) {
		BlockPos feet = this.feetPos(level, chest, work);
		if (feet.equals(this.walkingTo) && this.golem.tickCount % 15 != 0 && !this.golem.getNavigation().isDone()) {
			this.stuckTicks = this.golem.getNavigation().isStuck() ? this.stuckTicks + 1 : this.stuckTicks;
			if (this.stuckTicks > 40) {
				this.giveUpWalk(chest, work);
			}
			return;
		}
		boolean started = this.golem.getNavigation().moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0);
		if (!started) {
			BlockPos above = work.above();
			if (!above.equals(feet)) {
				started = this.golem.getNavigation().moveTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 1.0);
			}
		}
		if (!started) {
			this.golem.getMoveControl().setWantedPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0);
			if (++this.stuckTicks > 40) {
				this.giveUpWalk(chest, work);
			}
			return;
		}
		this.walkingTo = feet;
		this.stuckTicks = 0;
	}

	private void giveUpWalk(BlockPos chest, BlockPos work) {
		this.skipUntil.put(work, this.golem.tickCount + 30);
		this.stuckTicks = 0;
		this.walkingTo = null;
		this.golem.getNavigation().stop();
		if (FarmLayout.isDitch(chest, work)) {
			this.ringCursor++;
		}
	}

	private BlockPos feetPos(ServerLevel level, BlockPos chest, BlockPos work) {
		if (work.getY() < FarmLayout.ground(chest).getY()) {
			int dx = Integer.signum(chest.getX() - work.getX());
			int dz = Integer.signum(chest.getZ() - work.getZ());
			BlockPos bank = new BlockPos(work.getX() + dx, chest.getY(), work.getZ() + dz);
			if (this.standable(level, bank)) {
				return bank;
			}
		}
		if (work.getY() > chest.getY() && this.standable(level, work)) {
			return work;
		}
		BlockPos above = work.above();
		if (!FarmLayout.isDitch(chest, work) && this.standable(level, above)) {
			return above;
		}
		int dx = Integer.signum(chest.getX() - work.getX());
		int dz = Integer.signum(chest.getZ() - work.getZ());
		BlockPos inward = new BlockPos(work.getX() + dx, work.getY() + 1, work.getZ() + dz);
		if (this.standable(level, inward)) {
			return inward;
		}
		if (this.standable(level, above)) {
			return above;
		}
		return inward;
	}

	private boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	private boolean closeEnough(BlockPos work) {
		double dx = this.golem.getX() - (work.getX() + 0.5);
		double dy = this.golem.getY() - work.getY();
		double dz = this.golem.getZ() - (work.getZ() + 0.5);
		return dx * dx + dz * dz <= REACH_SQUARED && Math.abs(dy) <= 2.5;
	}

	private void lookAt(BlockPos pos) {
		this.golem.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);
	}

	private enum TaskKind {
		HARVEST, DIG, FILL, WATER, TILL, PLANT
	}

	private record Task(TaskKind kind, BlockPos pos) {
	}
}
