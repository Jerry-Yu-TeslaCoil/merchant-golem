package com.tradinggolem.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls a non-emerald stack from the home chest, walks the aisle in front of a villager's
 * face, and sells through that opening. It does not step into the cell.
 */
public final class TradingGolemWorkGoal extends Goal {
	private final TradingGolem golem;
	private final Set<UUID> skippedVillagers = new HashSet<>();
	@Nullable
	private UUID walkingTo;
	@Nullable
	private UUID approachVillager;
	private int standAttempt;
	private int stuckTicks;
	private int tradePause;
	private int chestHoldTicks;

	public TradingGolemWorkGoal(TradingGolem golem) {
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
			this.idleNearChest();
			return;
		}

		this.ensureChest(level);
		if (this.golem.chestPos == null || !ChestStash.isChest(level, this.golem.chestPos)) {
			this.golem.getNavigation().stop();
			return;
		}

		if (this.chestHoldTicks > 0) {
			this.chestHoldTicks--;
			this.golem.getNavigation().stop();
			return;
		}

		if (this.needsDeposit()) {
			this.returnHome(level);
			return;
		}

		if (this.golem.carried.isEmpty()) {
			this.fetchGoods(level);
			return;
		}

		this.sellCarried(level);
	}

	private boolean needsDeposit() {
		if (!this.golem.carried.isEmpty() && this.golem.failedItems.contains(itemKey(this.golem.carried))) {
			return true;
		}
		return this.golem.carried.isEmpty() && !this.golem.rewards.isEmpty();
	}

	private void ensureChest(ServerLevel level) {
		BlockPos bound = this.golem.chestPos;
		if (bound != null) {
			return;
		}
		if (this.golem.tickCount % 40 != 0) {
			return;
		}
		AABB area = this.golem.getBoundingBox().inflate(12.0);
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
			if (!ChestStash.isChest(level, pos)) {
				continue;
			}
			if (this.claimedByAnother(level, pos)) {
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
		BlockPos connected = connectedChest(level, pos);
		AABB area = new AABB(pos).inflate(64.0);
		for (TradingGolem other : level.getEntitiesOfClass(TradingGolem.class, area, entity -> entity != this.golem && entity.isAlive())) {
			BlockPos claimed = other.chestPos();
			if (pos.equals(claimed) || (connected != null && connected.equals(claimed))) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static BlockPos connectedChest(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return null;
		}
		return ChestBlock.getConnectedBlockPos(pos, state);
	}

	private void idleNearChest() {
		if (!this.golem.nearChest()) {
			this.walkToChest();
			return;
		}
		this.golem.getNavigation().stop();
		this.golem.closeChest();
	}

	private void returnHome(ServerLevel level) {
		if (!this.golem.nearChest()) {
			this.walkToChest();
			return;
		}
		Container container = ChestStash.get(level, this.golem.chestPos);
		if (container == null) {
			return;
		}
		this.golem.openChest(container, this.golem.chestPos);
		this.deposit(level, container, this.golem.carried);
		this.golem.setCarried(ItemStack.EMPTY);
		List<ItemStack> rewards = new java.util.ArrayList<>(this.golem.rewards);
		this.golem.rewards.clear();
		for (ItemStack reward : rewards) {
			this.deposit(level, container, reward);
		}
		this.golem.getNavigation().stop();
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

	private void fetchGoods(ServerLevel level) {
		if (!this.golem.nearChest()) {
			this.walkToChest();
			return;
		}
		if (this.golem.retryAfterCooldown) {
			this.golem.failedItems.clear();
			this.skippedVillagers.clear();
			this.golem.retryAfterCooldown = false;
		}
		Container container = ChestStash.get(level, this.golem.chestPos);
		if (container == null) {
			return;
		}
		this.golem.openChest(container, this.golem.chestPos);
		if (!this.takeNext(container)) {
			this.golem.closeChest();
			if (!this.golem.failedItems.isEmpty()) {
				this.golem.retryAfterCooldown = true;
				this.golem.cooldown = TradingRanges.RETRY_COOLDOWN_TICKS;
			} else {
				this.golem.cooldown = TradingRanges.IDLE_COOLDOWN_TICKS;
			}
		}
	}

	private boolean takeNext(Container container) {
		List<Integer> slots = ChestStash.sellableSlots(container);
		if (slots.isEmpty()) {
			return false;
		}
		int size = slots.size();
		for (int offset = 0; offset < size; offset++) {
			int index = Math.floorMod(this.golem.scanCursor + offset, size);
			ItemStack sample = container.getItem(slots.get(index));
			if (sample.isEmpty() || this.golem.failedItems.contains(itemKey(sample))) {
				continue;
			}
			int want = Math.min(sample.getMaxStackSize(), 64);
			ItemStack taken = ChestStash.extract(container, sample.getItem(), want);
			if (taken.isEmpty()) {
				continue;
			}
			this.golem.setCarried(taken);
			this.golem.scanCursor = index + 1;
			this.skippedVillagers.clear();
			this.walkingTo = null;
			this.approachVillager = null;
			this.standAttempt = 0;
			this.stuckTicks = 0;
			this.tradePause = 0;
			this.chestHoldTicks = 12;
			return true;
		}
		return false;
	}

	private void sellCarried(ServerLevel level) {
		this.golem.closeChest();
		List<Villager> buyers = this.findBuyers(level);
		if (buyers.isEmpty()) {
			if (!this.golem.failedItems.contains(itemKey(this.golem.carried))) {
				this.golem.failedItems.add(itemKey(this.golem.carried));
				level.playSound(null, this.golem.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.7F, 1.15F);
			}
			this.walkingTo = null;
			this.approachVillager = null;
			this.standAttempt = 0;
			return;
		}

		for (Villager buyer : buyers) {
			if (!VillagerWindow.canTrade(level, buyer, this.golem.getX(), this.golem.getY(), this.golem.getZ())) {
				continue;
			}
			this.golem.getLookControl().setLookAt(buyer, 30.0F, 30.0F);
			this.golem.getNavigation().stop();
			this.walkingTo = null;
			this.stuckTicks = 0;
			if (this.tradePause > 0) {
				this.tradePause--;
				return;
			}
			if (this.executeTrade(level, buyer)) {
				this.tradePause = TradingRanges.TRADE_PAUSE_TICKS;
				this.standAttempt = 0;
			} else {
				this.skippedVillagers.add(buyer.getUUID());
			}
			return;
		}

		this.approach(level, buyers.getFirst());
	}

	private void approach(ServerLevel level, Villager buyer) {
		if (this.approachVillager == null || !this.approachVillager.equals(buyer.getUUID())) {
			this.approachVillager = buyer.getUUID();
			this.walkingTo = null;
			this.standAttempt = 0;
			this.stuckTicks = 0;
		}
		if (buyer.getUUID().equals(this.walkingTo) && this.golem.getNavigation().isDone()) {
			this.standAttempt++;
			this.walkingTo = null;
			this.stuckTicks = 0;
		}
		if (buyer.getUUID().equals(this.walkingTo) && this.golem.tickCount % 15 != 0 && !this.golem.getNavigation().isDone()) {
			this.stuckTicks = this.golem.getNavigation().isStuck() ? this.stuckTicks + 1 : 0;
			if (this.stuckTicks > 50) {
				this.standAttempt++;
				this.walkingTo = null;
				this.stuckTicks = 0;
				this.golem.getNavigation().stop();
			}
			return;
		}

		List<BlockPos> stands = VillagerWindow.stands(level, buyer, this.golem);
		if (stands.isEmpty() || this.standAttempt >= stands.size()) {
			this.skippedVillagers.add(buyer.getUUID());
			this.walkingTo = null;
			this.approachVillager = null;
			this.standAttempt = 0;
			return;
		}
		BlockPos stand = stands.get(this.standAttempt);
		boolean started = this.golem.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.05);
		if (!started) {
			this.standAttempt++;
			this.walkingTo = null;
			return;
		}
		this.walkingTo = buyer.getUUID();
		this.stuckTicks = 0;
	}

	private List<Villager> findBuyers(ServerLevel level) {
		AABB area = this.golem.getBoundingBox().inflate(
				TradingRanges.VILLAGER_SEARCH,
				TradingRanges.VILLAGER_SEARCH_VERTICAL,
				TradingRanges.VILLAGER_SEARCH
		);
		Container chest = ChestStash.get(level, this.golem.chestPos);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, villager -> this.canConsider(villager, chest));
		villagers.sort(Comparator.comparingDouble(this.golem::distanceToSqr));
		return villagers;
	}

	private boolean canConsider(Villager villager, @Nullable Container chest) {
		if (!villager.isAlive() || villager.isBaby() || villager.isSleeping() || villager.isRemoved()) {
			return false;
		}
		if (villager.getTradingPlayer() != null) {
			return false;
		}
		if (this.skippedVillagers.contains(villager.getUUID())) {
			return false;
		}
		return this.findOffer(villager, chest) != null;
	}

	@Nullable
	private MerchantOffer findOffer(Villager villager, @Nullable Container chest) {
		MerchantOffers offers = villager.getOffers();
		for (MerchantOffer offer : offers) {
			if (offer.isOutOfStock()) {
				continue;
			}
			ItemStack result = offer.getResult();
			if (!result.is(Items.EMERALD) || result.isEmpty()) {
				continue;
			}
			ItemStack costA = offer.getCostA();
			if (costA.isEmpty() || costA.is(Items.EMERALD) || !offer.getItemCostA().test(this.golem.carried)) {
				continue;
			}
			if (this.golem.carried.getCount() < costA.getCount()) {
				continue;
			}
			ItemStack costB = offer.getCostB();
			if (!costB.isEmpty()) {
				int available = chest == null ? 0 : ChestStash.count(chest, costB.getItem());
				if (ItemStack.isSameItem(this.golem.carried, costB)) {
					available += Math.max(0, this.golem.carried.getCount() - costA.getCount());
				}
				if (available < costB.getCount()) {
					continue;
				}
			}
			return offer;
		}
		return null;
	}

	private boolean executeTrade(ServerLevel level, Villager villager) {
		Container chest = ChestStash.get(level, this.golem.chestPos);
		MerchantOffer offer = this.findOffer(villager, chest);
		if (offer == null) {
			return false;
		}
		ItemStack costB = offer.getCostB();
		ItemStack second = ItemStack.EMPTY;
		if (!costB.isEmpty()) {
			if (chest == null) {
				return false;
			}
			second = ChestStash.extract(chest, costB.getItem(), costB.getCount());
			if (second.getCount() < costB.getCount()) {
				ChestStash.insert(chest, second);
				return false;
			}
		}
		if (!offer.satisfiedBy(this.golem.carried, second)) {
			if (!second.isEmpty() && chest != null) {
				ChestStash.insert(chest, second);
			}
			return false;
		}
		String soldKey = itemKey(this.golem.carried);
		if (!offer.take(this.golem.carried, second)) {
			if (!second.isEmpty() && chest != null) {
				ChestStash.insert(chest, second);
			}
			return false;
		}
		if (!second.isEmpty() && chest != null) {
			ChestStash.insert(chest, second);
		}
		this.golem.setCarried(this.golem.carried.isEmpty() ? ItemStack.EMPTY : this.golem.carried);
		ItemStack result = offer.assemble();
		villager.notifyTrade(offer);
		this.addReward(result);
		this.golem.failedItems.remove(soldKey);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9F, 1.0F);
		level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				villager.getX(),
				villager.getEyeY(),
				villager.getZ(),
				8,
				0.3,
				0.35,
				0.3,
				0.02
		);
		return true;
	}

	private void addReward(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (ItemStack existing : this.golem.rewards) {
			if (!ItemStack.isSameItemSameComponents(existing, stack)) {
				continue;
			}
			int room = existing.getMaxStackSize() - existing.getCount();
			if (room <= 0) {
				continue;
			}
			int move = Math.min(room, stack.getCount());
			existing.grow(move);
			stack.shrink(move);
			if (stack.isEmpty()) {
				return;
			}
		}
		this.golem.rewards.add(stack);
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
		BlockPos above = pos.above();
		double x = above.getX() + 0.5;
		double y = above.getY();
		double z = above.getZ() + 0.5;
		if (!VillagerWindow.isStandable(this.golem.level(), above)) {
			x = pos.getX() + 0.5;
			y = pos.getY();
			z = pos.getZ() + 0.5;
		}
		this.golem.getNavigation().moveTo(x, y, z, 1.0);
	}

	static String itemKey(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
