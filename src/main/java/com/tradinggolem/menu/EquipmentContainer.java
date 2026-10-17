package com.tradinggolem.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class EquipmentContainer implements Container {
	public static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND
	};

	private final LivingEntity entity;

	public EquipmentContainer(LivingEntity entity) {
		this.entity = entity;
	}

	@Override
	public int getContainerSize() {
		return SLOTS.length;
	}

	@Override
	public boolean isEmpty() {
		for (EquipmentSlot slot : SLOTS) {
			if (!this.entity.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.entity.getItemBySlot(SLOTS[slot]);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack existing = this.getItem(slot);
		if (existing.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = existing.split(amount);
		this.setItem(slot, existing.isEmpty() ? ItemStack.EMPTY : existing);
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack existing = this.getItem(slot);
		this.setItem(slot, ItemStack.EMPTY);
		return existing;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.entity.setItemSlot(SLOTS[slot], stack);
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		for (EquipmentSlot slot : SLOTS) {
			this.entity.setItemSlot(slot, ItemStack.EMPTY);
		}
	}
}
