package com.tradinggolem.entity;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.block.SoldierChestBlock;
import com.tradinggolem.block.SoldierChestBlockEntity;
import com.tradinggolem.menu.SoldierMenu;
import com.tradinggolem.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Soldier extends Villager {
	private static final Map<GlobalPos, UUID> BY_CHEST = new HashMap<>();

	@Nullable
	private BlockPos chestPos;
	@Nullable
	private ResourceKey<VillagerType> villagerType;
	private boolean droppedCarried;
	private boolean dismissed;
	private boolean checkedJob;

	public Soldier(EntityType<? extends Soldier> type, Level level) {
		super(type, level);
		this.offers = new MerchantOffers();
		this.setPersistenceRequired();
	}

	public void bind(BlockPos chestPos, ResourceKey<VillagerType> villagerType) {
		this.chestPos = chestPos.immutable();
		this.villagerType = villagerType;
	}

	@Nullable
	public BlockPos chestPos() {
		return this.chestPos;
	}

	public static void remember(ServerLevel level, BlockPos chestPos, UUID soldierId) {
		BY_CHEST.put(GlobalPos.of(level.dimension(), chestPos), soldierId);
	}

	public static void forget(ServerLevel level, BlockPos chestPos, UUID soldierId) {
		GlobalPos key = GlobalPos.of(level.dimension(), chestPos);
		if (soldierId.equals(BY_CHEST.get(key))) {
			BY_CHEST.remove(key);
		}
	}

	@Nullable
	public static Soldier find(ServerLevel level, BlockPos chestPos, @Nullable UUID soldierId) {
		UUID id = soldierId;
		if (id == null) {
			id = BY_CHEST.get(GlobalPos.of(level.dimension(), chestPos));
		}
		if (id != null && level.getEntity(id) instanceof Soldier soldier && soldier.canBeFound()) {
			return soldier;
		}
		AABB area = new AABB(chestPos).inflate(256.0);
		List<Soldier> found = level.getEntitiesOfClass(Soldier.class, area, soldier -> chestPos.equals(soldier.chestPos) && soldier.canBeFound());
		return found.isEmpty() ? null : found.getFirst();
	}

	private boolean canBeFound() {
		return this.isAlive() && !this.dismissed && !this.isRemoved();
	}

	/**
	 * The job-site ticket was already released by the villager we replaced.
	 * Take it again so this soldier is the one holding the workstation.
	 */
	public void claimJobSite(ServerLevel level) {
		if (this.chestPos == null) {
			return;
		}
		this.releasePoi(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
		Optional<BlockPos> taken = level.getPoiManager().take(
				holder -> holder.is(ModVillagers.SOLDIER_POI),
				(holder, pos) -> this.chestPos.equals(pos),
				this.chestPos,
				1
		);
		if (taken.isEmpty()) {
			TradingGolemMod.LOGGER.warn("士兵没能占住工作站点 {}", this.chestPos);
			return;
		}
		this.getBrain().setMemory(
				net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
				GlobalPos.of(level.dimension(), this.chestPos)
		);
	}

	public void dismissChestBroken() {
		if (this.dismissed || this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.dismissed = true;
		if (this.isPassenger()) {
			this.stopRiding();
		}
		this.dropCarried(level);
		this.spawnUnemployed(level);
		this.unlink(level);
		this.discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.dismissed || this.level().isClientSide() || this.chestPos == null || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		remember(level, this.chestPos, this.getUUID());
		if (!this.checkedJob) {
			this.checkedJob = true;
			Optional<GlobalPos> memory = this.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			if (memory.isEmpty() || !this.chestPos.equals(memory.get().pos())) {
				this.claimJobSite(level);
			}
		}
		if (this.tickCount % 20 != 0 || !level.isLoaded(this.chestPos)) {
			return;
		}
		if (!(level.getBlockState(this.chestPos).getBlock() instanceof SoldierChestBlock)) {
			this.dismissChestBroken();
		}
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			this.dropCarried(level);
			if (this.chestPos != null) {
				level.getPoiManager().release(this.chestPos);
			}
			this.unlink(level);
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy() && this.level() instanceof ServerLevel level && this.chestPos != null) {
			this.releasePoi(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			this.releasePoi(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE);
			if (reason != RemovalReason.KILLED) {
				level.getPoiManager().release(this.chestPos);
			}
		}
		super.remove(reason);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			SoldierMenu.open(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canRestock() {
		return false;
	}

	@Override
	public boolean shouldRestock(ServerLevel level) {
		return false;
	}

	@Override
	protected void updateTrades(ServerLevel level) {
	}

	@Override
	public boolean canBreed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	private void dropCarried(ServerLevel level) {
		if (this.droppedCarried) {
			return;
		}
		this.droppedCarried = true;
		SimpleContainer inventory = this.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				this.spawnItem(level, stack.copy());
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			this.spawnItem(level, stack.copy());
			this.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	private void spawnItem(ServerLevel level, ItemStack stack) {
		ItemEntity entity = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack);
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	private void spawnUnemployed(ServerLevel level) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			return;
		}
		ResourceKey<VillagerType> type = this.villagerType == null ? VillagerData.DEFAULT_TYPE : this.villagerType;
		villager.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
		villager.setYHeadRot(this.getYHeadRot());
		villager.setYBodyRot(this.yBodyRot);
		villager.setPersistenceRequired();
		villager.setVillagerData(Villager.createDefaultVillagerData()
				.withType(level.registryAccess(), type)
				.withProfession(level.registryAccess(), VillagerProfession.NONE));
		villager.setVillagerDataFinalized(true);
		if (this.hasCustomName()) {
			villager.setCustomName(this.getCustomName());
			villager.setCustomNameVisible(this.isCustomNameVisible());
		}
		level.addFreshEntity(villager);
	}

	private void unlink(ServerLevel level) {
		if (this.chestPos == null) {
			return;
		}
		forget(level, this.chestPos, this.getUUID());
		if (level.getBlockEntity(this.chestPos) instanceof SoldierChestBlockEntity chest && this.getUUID().equals(chest.soldierId())) {
			chest.setSoldierId(null);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("ChestPos", BlockPos.CODEC, this.chestPos);
		if (this.villagerType != null) {
			output.putString("VillagerType", this.villagerType.identifier().toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.chestPos = input.read("ChestPos", BlockPos.CODEC).orElse(null);
		String typeName = input.getStringOr("VillagerType", "");
		if (!typeName.isEmpty()) {
			Identifier id = Identifier.tryParse(typeName);
			if (id != null) {
				this.villagerType = ResourceKey.create(Registries.VILLAGER_TYPE, id);
			}
		}
		this.setPersistenceRequired();
	}
}
