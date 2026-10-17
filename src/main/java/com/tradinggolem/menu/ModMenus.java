package com.tradinggolem.menu;

import com.tradinggolem.TradingGolemMod;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final ExtendedMenuType<SoldierMenu, SoldierScreenData> SOLDIER = registerSoldier();

	private ModMenus() {
	}

	public static void register() {
	}

	private static ExtendedMenuType<SoldierMenu, SoldierScreenData> registerSoldier() {
		ResourceKey<MenuType<?>> key = ResourceKey.create(Registries.MENU, TradingGolemMod.id("soldier"));
		return Registry.register(
				BuiltInRegistries.MENU,
				key,
				new ExtendedMenuType<>(SoldierMenu::client, SoldierScreenData.STREAM_CODEC)
		);
	}
}
