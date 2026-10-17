package com.tradinggolem.entity;

import com.tradinggolem.TradingGolemMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntityTypes {
	public static final EntityType<TradingGolem> TRADING_GOLEM = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			key("trading_golem"),
			EntityType.Builder.of(TradingGolem::new, MobCategory.MISC)
					.sized(0.6F, 0.95F)
					.eyeHeight(0.72F)
					.fireImmune()
					.clientTrackingRange(10)
					.build(key("trading_golem"))
	);
	public static final EntityType<Soldier> SOLDIER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			key("soldier"),
			EntityType.Builder.of(Soldier::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(key("soldier"))
	);
	public static final EntityType<FarmingGolem> FARMING_GOLEM = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			key("farming_golem"),
			EntityType.Builder.of(FarmingGolem::new, MobCategory.MISC)
					.sized(0.6F, 0.95F)
					.eyeHeight(0.72F)
					.fireImmune()
					.clientTrackingRange(10)
					.build(key("farming_golem"))
	);

	private ModEntityTypes() {
	}

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, TradingGolemMod.id(path));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(TRADING_GOLEM, TradingGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(FARMING_GOLEM, FarmingGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, Soldier.createAttributes());
	}
}
