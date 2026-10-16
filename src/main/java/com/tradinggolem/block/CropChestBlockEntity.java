package com.tradinggolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CropChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
	private static final int SLOTS = 27;
	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private final ChestLidController lidController = new ChestLidController();
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playLidSound(level, pos, SoundEvents.CHEST_OPEN);
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playLidSound(level, pos, SoundEvents.CHEST_CLOSE);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
			level.blockEvent(pos, state.getBlock(), 1, newCount);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == CropChestBlockEntity.this;
		}
	};

	public CropChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CROP_CHEST_ENTITY, pos, state);
	}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.trading_golem.crop_chest");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.threeRows(containerId, inventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, CropChestBlockEntity chest) {
		chest.lidController.tickLid();
	}

	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == 1) {
			this.lidController.shouldBeOpen(type > 0);
			return true;
		}
		return super.triggerEvent(id, type);
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (this.isRemoved() || user.getLivingEntity().isSpectator()) {
			return;
		}
		this.openersCounter.incrementOpeners(
				user.getLivingEntity(),
				this.getLevel(),
				this.getBlockPos(),
				this.getBlockState(),
				user.getContainerInteractionRange()
		);
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (this.isRemoved() || user.getLivingEntity().isSpectator()) {
			return;
		}
		this.openersCounter.decrementOpeners(user.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
	}

	public void recheckOpen() {
		if (this.getLevel() != null) {
			this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	@Override
	public float getOpenNess(float partialTick) {
		return this.lidController.getOpenness(partialTick);
	}

	private static void playLidSound(Level level, BlockPos pos, SoundEvent sound) {
		level.playSound(
				null,
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				sound,
				SoundSource.BLOCKS,
				0.5F,
				level.getRandom().nextFloat() * 0.1F + 0.9F
		);
	}
}
