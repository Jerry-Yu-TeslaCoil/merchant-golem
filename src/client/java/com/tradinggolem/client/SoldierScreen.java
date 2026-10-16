package com.tradinggolem.client;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.entity.SoldierOrder;
import com.tradinggolem.entity.SoldierWeaponMode;
import com.tradinggolem.menu.SoldierMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SoldierScreen extends AbstractContainerScreen<SoldierMenu> {
	private static final Identifier INFO = TradingGolemMod.id("textures/gui/soldier_info.png");
	private static final Identifier INVENTORY = TradingGolemMod.id("textures/gui/soldier_inventory.png");
	private static final int TEXT = 0xFF404040;

	private Tab tab = Tab.INFO;
	private Button infoTab;
	private Button ordersTab;
	private Button inventoryTab;
	private Button orderButton;
	private Button weaponButton;
	private Button rideButton;

	public SoldierScreen(SoldierMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 198);
		this.inventoryLabelY = 108;
	}

	@Override
	protected void init() {
		super.init();
		int tabY = this.topPos - 22;
		this.infoTab = this.addRenderableWidget(this.tabButton(0, tabY, "gui.trading_golem.soldier.info", Tab.INFO));
		this.ordersTab = this.addRenderableWidget(this.tabButton(1, tabY, "gui.trading_golem.soldier.orders", Tab.ORDERS));
		this.inventoryTab = this.addRenderableWidget(this.tabButton(2, tabY, "gui.trading_golem.soldier.inventory", Tab.INVENTORY));
		this.orderButton = this.addRenderableWidget(this.commandButton(32, SoldierMenu.BUTTON_ORDER));
		this.weaponButton = this.addRenderableWidget(this.commandButton(58, SoldierMenu.BUTTON_WEAPON));
		this.rideButton = this.addRenderableWidget(this.commandButton(84, SoldierMenu.BUTTON_RIDE));
		this.applyTab();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		Identifier texture = this.tab == Tab.INVENTORY ? INVENTORY : INFO;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
		this.refreshCommands();
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
		if (this.tab == Tab.INVENTORY) {
			graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		}
		if (this.tab == Tab.INFO) {
			Component lord = this.menu.lordName().isEmpty()
					? Component.translatable("gui.trading_golem.soldier.lord_unknown")
					: Component.literal(this.menu.lordName());
			graphics.text(this.font, Component.translatable("gui.trading_golem.soldier.lord", lord), 8, 32, TEXT, false);
			graphics.text(this.font, Component.translatable("gui.trading_golem.soldier.demands"), 8, 52, TEXT, false);
			graphics.text(this.font, Component.translatable("gui.trading_golem.soldier.demands_empty"), 8, 66, TEXT, false);
		}
	}

	private Button tabButton(int index, int y, String key, Tab tab) {
		return Button.builder(Component.translatable(key), button -> this.select(tab))
				.bounds(this.leftPos + 4 + index * 58, y, 54, 20)
				.build();
	}

	private Button commandButton(int y, int id) {
		return Button.builder(Component.empty(), button -> {
			if (this.minecraft != null && this.minecraft.gameMode != null) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
			}
		}).bounds(this.leftPos + 8, this.topPos + y, 160, 20).build();
	}

	private void select(Tab tab) {
		this.tab = tab;
		this.applyTab();
	}

	private void applyTab() {
		this.infoTab.active = this.tab != Tab.INFO;
		this.ordersTab.active = this.tab != Tab.ORDERS;
		this.inventoryTab.active = this.tab != Tab.INVENTORY;
		boolean orders = this.tab == Tab.ORDERS;
		this.orderButton.visible = orders;
		this.weaponButton.visible = orders;
		this.rideButton.visible = orders;
		this.menu.setInventoryVisible(this.tab == Tab.INVENTORY);
	}

	private void refreshCommands() {
		this.orderButton.setMessage(Component.translatable("gui.trading_golem.soldier.order", label(this.menu.order())));
		this.weaponButton.setMessage(Component.translatable("gui.trading_golem.soldier.weapon", label(this.menu.weaponMode())));
		this.rideButton.setMessage(Component.translatable("gui.trading_golem.soldier.ride", Component.translatable(this.menu.ride() ? "gui.trading_golem.soldier.ride.on" : "gui.trading_golem.soldier.ride.off")));
	}

	private static Component label(SoldierOrder order) {
		return Component.translatable("gui.trading_golem.soldier.order." + order.name().toLowerCase());
	}

	private static Component label(SoldierWeaponMode mode) {
		return Component.translatable("gui.trading_golem.soldier.weapon." + mode.name().toLowerCase());
	}

	private enum Tab {
		INFO,
		ORDERS,
		INVENTORY
	}
}
