package com.tradinggolem.block;

import com.tradinggolem.TradingGolemMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public final class ModBlocks {
	public static final TradingChestBlock TRADING_CHEST = registerBlock();
	public static final BlockEntityType<TradingChestBlockEntity> TRADING_CHEST_ENTITY = registerEntity();
	public static final Item TRADING_CHEST_ITEM = registerItem();
	public static final CropChestBlock CROP_CHEST = registerCropBlock();
	public static final BlockEntityType<CropChestBlockEntity> CROP_CHEST_ENTITY = registerCropEntity();
	public static final Item CROP_CHEST_ITEM = registerCropItem();
	public static final SoldierChestBlock SOLDIER_CHEST = registerSoldierBlock();
	public static final BlockEntityType<SoldierChestBlockEntity> SOLDIER_CHEST_ENTITY = registerSoldierEntity();
	public static final Item SOLDIER_CHEST_ITEM = registerSoldierItem();

	private ModBlocks() {
	}

	private static TradingChestBlock registerBlock() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TradingGolemMod.id("trading_chest"));
		TradingChestBlock block = new TradingChestBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GREEN)
				.strength(2.5F)
				.sound(net.minecraft.world.level.block.SoundType.WOOD)
				.noOcclusion()
				.noLootTable()
				.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static BlockEntityType<TradingChestBlockEntity> registerEntity() {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, TradingGolemMod.id("trading_chest"));
		BlockEntityType<TradingChestBlockEntity> type = new BlockEntityType<>(TradingChestBlockEntity::new, Set.of(TRADING_CHEST));
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
	}

	private static Item registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradingGolemMod.id("trading_chest"));
		Item item = new BlockItem(TRADING_CHEST, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static CropChestBlock registerCropBlock() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TradingGolemMod.id("crop_chest"));
		CropChestBlock block = new CropChestBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(net.minecraft.world.level.material.MapColor.COLOR_YELLOW)
				.strength(2.5F)
				.sound(net.minecraft.world.level.block.SoundType.WOOD)
				.noOcclusion()
				.noLootTable()
				.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static BlockEntityType<CropChestBlockEntity> registerCropEntity() {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, TradingGolemMod.id("crop_chest"));
		BlockEntityType<CropChestBlockEntity> type = new BlockEntityType<>(CropChestBlockEntity::new, Set.of(CROP_CHEST));
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
	}

	private static Item registerCropItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradingGolemMod.id("crop_chest"));
		Item item = new BlockItem(CROP_CHEST, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static SoldierChestBlock registerSoldierBlock() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TradingGolemMod.id("soldier_chest"));
		SoldierChestBlock block = new SoldierChestBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)
				.strength(2.5F)
				.sound(net.minecraft.world.level.block.SoundType.WOOD)
				.noOcclusion()
				.noLootTable()
				.pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static BlockEntityType<SoldierChestBlockEntity> registerSoldierEntity() {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, TradingGolemMod.id("soldier_chest"));
		BlockEntityType<SoldierChestBlockEntity> type = new BlockEntityType<>(SoldierChestBlockEntity::new, Set.of(SOLDIER_CHEST));
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
	}

	private static Item registerSoldierItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TradingGolemMod.id("soldier_chest"));
		Item item = new BlockItem(SOLDIER_CHEST, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(TRADING_CHEST_ITEM);
			output.accept(CROP_CHEST_ITEM);
			output.accept(SOLDIER_CHEST_ITEM);
		});
	}
}
