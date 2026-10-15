package com.tradinggolem.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TradingGolem extends AbstractGolem implements ContainerUser {
	private static final Codec<List<ItemStack>> ITEM_LIST_CODEC = ItemStack.CODEC.listOf();
	private static final Codec<List<String>> STRING_LIST_CODEC = Codec.STRING.listOf();

	@Nullable
	BlockPos chestPos;
	ItemStack carried = ItemStack.EMPTY;
	final List<ItemStack> rewards = new ArrayList<>();
	final Set<String> failedItems = new HashSet<>();
	int cooldown;
	int scanCursor;
	boolean retryAfterCooldown;
	@Nullable
	private BlockPos openedChestPos;

	public TradingGolem(EntityType<? extends TradingGolem> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.FOLLOW_RANGE, TradingRanges.VILLAGER_SEARCH)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TradingGolemWorkGoal(this));
	}

	public void bindChest(BlockPos pos) {
		this.chestPos = pos.immutable();
	}

	@Nullable
	public BlockPos chestPos() {
		return this.chestPos;
	}

	public ItemStack carried() {
		return this.carried;
	}

	void setCarried(ItemStack stack) {
		this.carried = stack;
		this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos) {
		return pos.equals(this.openedChestPos);
	}

	@Override
	public double getContainerInteractionRange() {
		return 4.0;
	}

	void openChest(Container container, BlockPos pos) {
		if (pos.equals(this.openedChestPos)) {
			return;
		}
		this.closeChest();
		container.startOpen(this);
		this.openedChestPos = pos.immutable();
	}

	void closeChest() {
		BlockPos pos = this.openedChestPos;
		this.openedChestPos = null;
		if (pos == null) {
			return;
		}
		Container container = ChestStash.get(this.level(), pos);
		if (container != null) {
			container.stopOpen(this);
		}
	}

	boolean nearChest() {
		BlockPos pos = this.chestPos;
		if (pos == null) {
			return false;
		}
		double dx = this.getX() - (pos.getX() + 0.5);
		double dy = this.getY() - pos.getY();
		double dz = this.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= TradingRanges.CHEST_HORIZONTAL * TradingRanges.CHEST_HORIZONTAL
				&& Math.abs(dy) <= TradingRanges.CHEST_VERTICAL;
	}

	@Override
	public void remove(RemovalReason reason) {
		if ((reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && this.level() instanceof ServerLevel server) {
			this.dropHeld(server);
		}
		this.closeChest();
		super.remove(reason);
	}

	private void dropHeld(ServerLevel level) {
		this.dropStack(level, this.carried);
		this.setCarried(ItemStack.EMPTY);
		for (ItemStack reward : this.rewards) {
			this.dropStack(level, reward);
		}
		this.rewards.clear();
	}

	private void dropStack(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity entity = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack.copy());
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.35F, 1.25F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("ChestPos", BlockPos.CODEC, this.chestPos);
		if (!this.carried.isEmpty()) {
			output.store("Carried", ItemStack.CODEC, this.carried);
		}
		if (!this.rewards.isEmpty()) {
			output.store("Rewards", ITEM_LIST_CODEC, this.rewards);
		}
		if (!this.failedItems.isEmpty()) {
			output.store("FailedItems", STRING_LIST_CODEC, List.copyOf(this.failedItems));
		}
		output.putInt("Cooldown", this.cooldown);
		output.putInt("ScanCursor", this.scanCursor);
		output.putBoolean("RetryAfterCooldown", this.retryAfterCooldown);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.chestPos = input.read("ChestPos", BlockPos.CODEC).orElse(null);
		this.setCarried(input.read("Carried", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		this.rewards.clear();
		this.rewards.addAll(input.read("Rewards", ITEM_LIST_CODEC).orElse(List.of()));
		this.failedItems.clear();
		this.failedItems.addAll(input.read("FailedItems", STRING_LIST_CODEC).orElse(List.of()));
		this.cooldown = input.getIntOr("Cooldown", 0);
		this.scanCursor = input.getIntOr("ScanCursor", 0);
		this.retryAfterCooldown = input.getBooleanOr("RetryAfterCooldown", false);
	}
}
