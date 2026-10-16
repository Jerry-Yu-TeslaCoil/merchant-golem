package com.tradinggolem.item;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.entity.ModEntityTypes;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	public static final Item TRADING_GOLEM_SPAWN_EGG = register(
			"trading_golem_spawn_egg",
			SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntityTypes.TRADING_GOLEM)
	);
	public static final Item FARMING_GOLEM_SPAWN_EGG = register(
			"farming_golem_spawn_egg",
			SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntityTypes.FARMING_GOLEM)
	);

	private ModItems() {
	}

	private static Item register(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradingGolemMod.id(name));
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(TRADING_GOLEM_SPAWN_EGG);
			output.accept(FARMING_GOLEM_SPAWN_EGG);
		});
	}
}
