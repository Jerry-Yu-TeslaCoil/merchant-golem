package com.tradinggolem.villager;

import com.google.common.collect.ImmutableSet;
import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.block.ModBlocks;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ModVillagers {
	public static final ResourceKey<PoiType> SOLDIER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TradingGolemMod.id("soldier"));
	public static final ResourceKey<VillagerProfession> SOLDIER_PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, TradingGolemMod.id("soldier"));

	private ModVillagers() {
	}

	public static void register() {
		PoiTypes.register(
				BuiltInRegistries.POINT_OF_INTEREST_TYPE,
				SOLDIER_POI,
				ImmutableSet.copyOf(ModBlocks.SOLDIER_CHEST.getStateDefinition().getPossibleStates()),
				1,
				1
		);
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, SOLDIER_PROFESSION, new VillagerProfession(
				Component.translatable("entity.trading_golem.soldier"),
				holder -> holder.is(SOLDIER_POI),
				holder -> holder.is(SOLDIER_POI),
				ImmutableSet.of(),
				ImmutableSet.of(),
				SoundEvents.VILLAGER_WORK_ARMORER,
				new Int2ObjectOpenHashMap<>()
		));
	}
}
