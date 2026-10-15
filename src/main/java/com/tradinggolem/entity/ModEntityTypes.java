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
			key(),
			EntityType.Builder.of(TradingGolem::new, MobCategory.MISC)
					.sized(0.6F, 0.95F)
					.eyeHeight(0.72F)
					.fireImmune()
					.clientTrackingRange(10)
					.build(key())
	);

	private ModEntityTypes() {
	}

	private static ResourceKey<EntityType<?>> key() {
		return ResourceKey.create(Registries.ENTITY_TYPE, TradingGolemMod.id("trading_golem"));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(TRADING_GOLEM, TradingGolem.createAttributes());
	}
}
