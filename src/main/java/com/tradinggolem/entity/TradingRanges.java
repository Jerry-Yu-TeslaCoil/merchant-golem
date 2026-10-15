package com.tradinggolem.entity;

/**
 * Distances are chosen for a villager trading hall, where each villager is locked in a one-block cell.
 * The golem trades from the aisle. It does not need to stand against the villager.
 */
public final class TradingRanges {
	/** Horizontal reach, in blocks. Farther than one block, short of trading through a whole building. */
	public static final double TRADE_HORIZONTAL = 3.0;
	/** Villagers standing on a slab, carpet stack, or one block up are still in reach. */
	public static final double TRADE_VERTICAL = 2.0;
	/** How far the golem will walk to find a buyer, measured from itself. */
	public static final double VILLAGER_SEARCH = 48.0;
	/** Horizontal distance at which the golem may take from or fill its chest. */
	public static final double CHEST_HORIZONTAL = 2.0;
	public static final double CHEST_VERTICAL = 2.5;
	/** Ticks to wait after every distinct item in the chest failed to sell. Villagers restock on their own schedule. */
	public static final int RETRY_COOLDOWN_TICKS = 300;
	public static final int IDLE_COOLDOWN_TICKS = 40;
	/** Pause between two successful sales so the trade is visible. */
	public static final int TRADE_PAUSE_TICKS = 8;

	private TradingRanges() {
	}

	public static boolean inTradeRange(double dx, double dy, double dz) {
		return dx * dx + dz * dz <= TRADE_HORIZONTAL * TRADE_HORIZONTAL && Math.abs(dy) <= TRADE_VERTICAL;
	}
}
