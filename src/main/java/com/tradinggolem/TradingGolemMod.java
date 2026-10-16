package com.tradinggolem;

import com.tradinggolem.block.ModBlocks;
import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TradingGolemMod implements ModInitializer {
	public static final String MOD_ID = "trading_golem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModEntityTypes.register();
		ModItems.register();
		LOGGER.info("交易傀儡已就绪");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
