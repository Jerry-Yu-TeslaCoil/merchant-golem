package com.tradinggolem.entity;

public enum SoldierWeaponMode {
	MELEE,
	RANGED,
	FREE;

	public static SoldierWeaponMode fromName(String name) {
		for (SoldierWeaponMode mode : values()) {
			if (mode.name().equals(name)) {
				return mode;
			}
		}
		return FREE;
	}
}
