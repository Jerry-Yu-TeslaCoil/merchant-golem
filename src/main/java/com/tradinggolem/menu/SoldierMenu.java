package com.tradinggolem.menu;

import com.tradinggolem.block.SoldierChestBlock;
import com.tradinggolem.block.SoldierChestBlockEntity;
import com.tradinggolem.entity.Soldier;
import com.tradinggolem.entity.SoldierOrder;
import com.tradinggolem.entity.SoldierWeaponMode;
import com.tradinggolem.entity.CampSite;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

public class SoldierMenu extends AbstractContainerMenu {
	public static final int BUTTON_ORDER = 0;
	public static final int BUTTON_WEAPON = 1;
	public static final int BUTTON_RIDE = 2;
	private static final int BACKPACK_SIZE = 8;
	private static final int EQUIPMENT_SIZE = EquipmentContainer.SLOTS.length;
	private static final int SOLDIER_SLOTS = BACKPACK_SIZE + EQUIPMENT_SIZE;

	private final ContainerData data;
	private final String lordName;
	@Nullable
	private final Soldier soldier;
	@Nullable
	private final SoldierChestBlockEntity chest;
	private final Player viewer;
	private boolean inventoryVisible;
	private String demands = "";

	public SoldierMenu(int containerId, Inventory playerInventory, Container backpack, Container equipment, ContainerData data, @Nullable Soldier soldier, @Nullable SoldierChestBlockEntity chest, String lordName) {
		super(ModMenus.SOLDIER, containerId);
		this.data = data;
		this.lordName = lordName;
		this.soldier = soldier;
		this.chest = chest;
		this.viewer = playerInventory.player;
		this.inventoryVisible = !playerInventory.player.level().isClientSide();
		for (int i = 0; i < BACKPACK_SIZE; i++) {
			int column = i % 4;
			int row = i / 4;
			this.addSlot(new ShownSlot(backpack, i, 80 + column * 18, 18 + row * 18));
		}
		int[] armorY = {18, 36, 54, 72};
		for (int i = 0; i < 4; i++) {
			this.addSlot(new EquipSlot(equipment, i, 8, armorY[i], EquipmentContainer.SLOTS[i]));
		}
		this.addSlot(new EquipSlot(equipment, 4, 26, 72, EquipmentSlot.MAINHAND));
		this.addSlot(new EquipSlot(equipment, 5, 44, 72, EquipmentSlot.OFFHAND));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new ShownSlot(playerInventory, column + row * 9 + 9, 8 + column * 18, 116 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			this.addSlot(new ShownSlot(playerInventory, column, 8 + column * 18, 174));
		}
		this.addDataSlots(data);
	}

	public static SoldierMenu client(int containerId, Inventory inventory, SoldierScreenData data) {
		SimpleContainerData sync = new SimpleContainerData(3);
		sync.set(0, data.order());
		sync.set(1, data.weapon());
		sync.set(2, data.ride());
		SoldierMenu menu = new SoldierMenu(containerId, inventory, new SimpleContainer(BACKPACK_SIZE), new SimpleContainer(EQUIPMENT_SIZE), sync, null, null, data.lordName());
		menu.demands = data.demands();
		return menu;
	}

	public static void open(ServerPlayer player, Soldier soldier) {
		ServerLevel level = player.level();
		BlockPos pos = soldier.chestPos();
		if (pos == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SoldierChestBlockEntity chest)) {
			return;
		}
		String lordName = chest.lordName();
		if (lordName.isEmpty() && chest.lord() != null) {
			ServerPlayer lord = level.getServer().getPlayerList().getPlayer(chest.lord());
			if (lord != null) {
				lordName = lord.getName().getString();
				chest.setLord(chest.lord(), lordName);
			}
		}
		String shown = lordName;
		String demandText = CampSite.demands(level, pos, soldier);
		player.openMenu(new ExtendedMenuProvider<SoldierScreenData>() {
			@Override
			public SoldierScreenData getScreenOpeningData(ServerPlayer serverPlayer) {
				return new SoldierScreenData(shown, chest.order().ordinal(), chest.weaponMode().ordinal(), chest.ride() ? 1 : 0, demandText);
			}

			@Override
			public Component getDisplayName() {
				return soldier.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
				SoldierMenu menu = server(containerId, inventory, soldier, chest, shown);
				menu.demands = demandText;
				return menu;
			}
		});
	}

	private static SoldierMenu server(int containerId, Inventory inventory, Soldier soldier, SoldierChestBlockEntity chest, String lordName) {
		ContainerData sync = new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case 0 -> chest.order().ordinal();
					case 1 -> chest.weaponMode().ordinal();
					case 2 -> chest.ride() ? 1 : 0;
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return 3;
			}
		};
		return new SoldierMenu(containerId, inventory, soldier.getInventory(), new EquipmentContainer(soldier), sync, soldier, chest, lordName);
	}

	public String lordName() {
		return this.lordName;
	}

	public SoldierOrder order() {
		return byOrdinal(SoldierOrder.values(), this.data.get(0), SoldierOrder.REST);
	}

	public SoldierWeaponMode weaponMode() {
		return byOrdinal(SoldierWeaponMode.values(), this.data.get(1), SoldierWeaponMode.FREE);
	}

	public boolean ride() {
		return this.data.get(2) != 0;
	}

	public void setDemands(String demands) {
		this.demands = demands == null ? "" : demands;
	}

	public java.util.List<Component> demandLines() {
		if (this.demands.isEmpty()) {
			return java.util.List.of();
		}
		java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
		for (String part : this.demands.split(",")) {
			String[] bits = part.split(":", 2);
			int count = bits.length > 1 ? parse(bits[1]) : 1;
			Component line = switch (bits[0]) {
				case "PICK" -> Component.translatable("gui.trading_golem.soldier.demand.pick");
				case "SHOVEL" -> Component.translatable("gui.trading_golem.soldier.demand.shovel");
				case "STONE" -> Component.translatable("gui.trading_golem.soldier.demand.stone", count);
				case "BED" -> Component.translatable("gui.trading_golem.soldier.demand.bed");
				case "FENCE" -> Component.translatable("gui.trading_golem.soldier.demand.fence");
				case "FOOD" -> Component.translatable("gui.trading_golem.soldier.demand.food", count);
				default -> null;
			};
			if (line != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	private static int parse(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException exception) {
			return 1;
		}
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.soldier == null || this.chest == null || !(this.soldier.level() instanceof ServerLevel level)) {
			return;
		}
		String next = CampSite.demands(level, this.chest.getBlockPos(), this.soldier);
		if (next.equals(this.demands)) {
			return;
		}
		this.demands = next;
		if (this.viewer instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, new SoldierDemandsPayload(next));
		}
	}

	public void setInventoryVisible(boolean inventoryVisible) {
		this.inventoryVisible = inventoryVisible;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.chest == null) {
			return false;
		}
		switch (id) {
			case BUTTON_ORDER -> this.chest.setOrder(next(SoldierOrder.values(), this.chest.order()));
			case BUTTON_WEAPON -> this.chest.setWeaponMode(next(SoldierWeaponMode.values(), this.chest.weaponMode()));
			case BUTTON_RIDE -> this.chest.setRide(!this.chest.ride());
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.soldier == null) {
			return true;
		}
		BlockPos pos = this.soldier.chestPos();
		return this.soldier.isAlive()
				&& player.distanceToSqr(this.soldier) < 64.0
				&& pos != null
				&& player.level().isLoaded(pos)
				&& player.level().getBlockState(pos).getBlock() instanceof SoldierChestBlock;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < SOLDIER_SLOTS) {
			if (!this.moveItemStackTo(stack, SOLDIER_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, SOLDIER_SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		return original;
	}

	private static <T> T byOrdinal(T[] values, int ordinal, T fallback) {
		if (ordinal < 0 || ordinal >= values.length) {
			return fallback;
		}
		return values[ordinal];
	}

	private static <T> T next(T[] values, T current) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) {
				return values[(i + 1) % values.length];
			}
		}
		return values[0];
	}

	private class ShownSlot extends Slot {
		private ShownSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean isActive() {
			return SoldierMenu.this.inventoryVisible;
		}
	}

	private final class EquipSlot extends ShownSlot {
		private final EquipmentSlot equipmentSlot;

		private EquipSlot(Container container, int slot, int x, int y, EquipmentSlot equipmentSlot) {
			super(container, slot, x, y);
			this.equipmentSlot = equipmentSlot;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (this.equipmentSlot == EquipmentSlot.MAINHAND || this.equipmentSlot == EquipmentSlot.OFFHAND) {
				return true;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			return equippable != null && equippable.slot() == this.equipmentSlot && equippable.allowedEntities().isEmpty();
		}

		@Override
		public int getMaxStackSize() {
			return this.equipmentSlot == EquipmentSlot.MAINHAND || this.equipmentSlot == EquipmentSlot.OFFHAND ? 64 : 1;
		}
	}
}
