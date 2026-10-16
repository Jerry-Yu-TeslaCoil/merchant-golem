package com.tradinggolem.villager;

/** Reads the villager food level. Game code must not reference the mixin class itself. */
public interface VillagerFoodAccess {
	int tradingGolem$getFoodLevel();

	void tradingGolem$setFoodLevel(int foodLevel);
}
