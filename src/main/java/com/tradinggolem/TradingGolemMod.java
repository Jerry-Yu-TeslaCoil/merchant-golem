package com.tradinggolem;

import com.tradinggolem.block.ModBlocks;
import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.item.ModItems;
import com.tradinggolem.menu.ModMenus;
import com.tradinggolem.menu.SoldierDemandsPayload;
import com.tradinggolem.menu.SoldierMenu;
import com.tradinggolem.entity.Soldier;
import com.tradinggolem.villager.ModVillagers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
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
		ModVillagers.register();
		ModMenus.register();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Enemy && source.getEntity() instanceof Soldier soldier && soldier.level() instanceof ServerLevel level) {
				soldier.grantKillXp(level);
			}
		});
		PayloadTypeRegistry.clientboundPlay().register(SoldierDemandsPayload.TYPE, SoldierDemandsPayload.STREAM_CODEC);
		LOGGER.info("领主已就绪");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
