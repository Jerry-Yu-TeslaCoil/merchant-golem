package com.tradinggolem.entity;

/**
 * Distances for a villager trading hall. Each villager sits in a one-block cell:
 * blocks around the feet, and an opening in front of the face. The golem trades
 * from the aisle, through that opening, the way a player clicks the face.
 */
public final class TradingRanges {
	/** Eye height used to judge reach, matching a player standing where the golem stands. */
	public static final double EYE_HEIGHT = 1.62;
	/** How close a free villager's hitbox must be, measured from that eye height. */
	public static final double FREE_REACH = 3.5;
	/**
	 * How close the face opening must be. Long enough to cover the aisle in front of a cell,
	 * including a counter, a slab, and a path that stops a block short.
	 */
	public static final double WINDOW_REACH = 5.0;
	/** How far the golem will walk to find a buyer, measured from itself. */
	public static final double VILLAGER_SEARCH = 48.0;
	/** Trading halls stack rows. Search well above and below the aisle. */
	public static final double VILLAGER_SEARCH_VERTICAL = 16.0;
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
}
