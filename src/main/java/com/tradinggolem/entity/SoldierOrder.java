package com.tradinggolem.entity;

public enum SoldierOrder {
	REST,
	PATROL,
	FOLLOW_DEFEND,
	FOLLOW_ATTACK;

	public static SoldierOrder fromName(String name) {
		for (SoldierOrder order : values()) {
			if (order.name().equals(name)) {
				return order;
			}
		}
		return REST;
	}
}
