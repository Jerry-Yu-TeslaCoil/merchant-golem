package com.tradinggolem.villager;

import com.tradinggolem.block.SoldierChestBlock;
import com.tradinggolem.block.SoldierChestBlockEntity;
import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.entity.Soldier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoldierRecruitment {
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

	private SoldierRecruitment() {
	}

	public static void onProfessionSet(Villager villager, VillagerData data) {
		if (villager instanceof Soldier || villager.level().isClientSide()) {
			return;
		}
		if (!data.profession().is(ModVillagers.SOLDIER_PROFESSION)) {
			return;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!PENDING.add(villager.getUUID())) {
			return;
		}
		level.getServer().execute(() -> {
			PENDING.remove(villager.getUUID());
			if (!villager.isAlive() || villager.isRemoved()) {
				return;
			}
			if (!villager.getVillagerData().profession().is(ModVillagers.SOLDIER_PROFESSION)) {
				return;
			}
			tryConvert(level, villager);
		});
	}

	private static void tryConvert(ServerLevel level, Villager villager) {
		BlockPos chestPos = jobSite(level, villager);
		if (chestPos == null || !(level.getBlockState(chestPos).getBlock() instanceof SoldierChestBlock)) {
			return;
		}
		Soldier existing = Soldier.find(level, chestPos, soldierId(level, chestPos));
		if (existing != null) {
			reject(level, villager);
			existing.claimJobSite(level);
			return;
		}

		ResourceKey<VillagerType> type = villager.getVillagerData().type().unwrapKey().orElse(VillagerData.DEFAULT_TYPE);
		Soldier soldier = ModEntityTypes.SOLDIER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (soldier == null) {
			return;
		}
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		soldier.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
		soldier.setYHeadRot(villager.getYHeadRot());
		soldier.setYBodyRot(villager.yBodyRot);
		soldier.bind(chestPos, type);
		soldier.setHealth(villager.getHealth());
		if (villager.hasCustomName()) {
			soldier.setCustomName(villager.getCustomName());
			soldier.setCustomNameVisible(villager.isCustomNameVisible());
		}
		moveItems(villager, soldier);
		level.addFreshEntity(soldier);
		soldier.setVillagerData(Villager.createDefaultVillagerData()
				.withType(level.registryAccess(), type)
				.withProfession(level.registryAccess(), ModVillagers.SOLDIER_PROFESSION)
				.withLevel(villager.getVillagerData().level()));
		soldier.setVillagerDataFinalized(true);
		soldier.refreshBrain(level);
		soldier.claimJobSite(level);
		if (level.getBlockEntity(chestPos) instanceof SoldierChestBlockEntity chest) {
			chest.setSoldierId(soldier.getUUID());
		}
		Soldier.remember(level, chestPos, soldier.getUUID());
		villager.discard();
		level.playSound(null, soldier.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, soldier.getX(), soldier.getEyeY(), soldier.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
	}

	private static void reject(ServerLevel level, Villager villager) {
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
		villager.refreshBrain(level);
	}

	private static UUID soldierId(ServerLevel level, BlockPos chestPos) {
		if (level.getBlockEntity(chestPos) instanceof SoldierChestBlockEntity chest) {
			return chest.soldierId();
		}
		return null;
	}

	private static BlockPos jobSite(ServerLevel level, Villager villager) {
		Optional<GlobalPos> memory = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (memory.isPresent() && memory.get().dimension().equals(level.dimension())) {
			return memory.get().pos();
		}
		return null;
	}

	private static void moveItems(Villager villager, Soldier soldier) {
		SimpleContainer from = villager.getInventory();
		SimpleContainer to = soldier.getInventory();
		int slots = Math.min(from.getContainerSize(), to.getContainerSize());
		for (int slot = 0; slot < slots; slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			to.setItem(slot, stack.copy());
			from.setItem(slot, ItemStack.EMPTY);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = villager.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			soldier.setItemSlot(slot, stack.copy());
			villager.setItemSlot(slot, ItemStack.EMPTY);
		}
		villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
	}
}
