package com.tradinggolem.entity;

import com.tradinggolem.block.SoldierChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** While resting and the camp is whole: hitch, stock the chest, then buy from villagers. */
public final class SoldierRestGoal extends Goal {
	private static final int FOOD_ON_BODY = 16;
	private static final int EMERALDS_ON_BODY = 64;
	private static final int ARROW_TARGET = 64;

	private final Soldier soldier;
	private final Set<UUID> skipped = new HashSet<>();
	private boolean sorted;
	private int pause;
	private int cooldown;
	private int standAttempt;
	@Nullable
	private UUID buyer;

	public SoldierRestGoal(Soldier soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.ready();
	}

	@Override
	public boolean canContinueToUse() {
		return this.ready();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.soldier.closeChest();
		this.soldier.getNavigation().stop();
		this.sorted = false;
		this.buyer = null;
		this.skipped.clear();
		this.pause = 0;
		this.standAttempt = 0;
	}

	@Override
	public void tick() {
		if (!(this.soldier.level() instanceof ServerLevel level) || this.soldier.chestPos() == null) {
			return;
		}
		if (this.pause > 0) {
			this.pause--;
			return;
		}
		BlockPos chest = this.soldier.chestPos();
		if (this.soldier.isPassenger()) {
			this.hitch(level, chest);
			return;
		}
		Container container = level.getBlockEntity(chest) instanceof Container found ? found : null;
		if (container == null) {
			return;
		}
		if (this.needsTack(level, chest, container)) {
			if (!this.near(chest)) {
				this.walk(chest);
				return;
			}
			this.soldier.openChest(container, chest);
			this.soldier.getNavigation().stop();
			this.equipHorse(level, chest, container);
		}
		if (this.cooldown > 0) {
			this.cooldown--;
			this.idle(chest);
			return;
		}
		if (!this.sorted) {
			if (!this.near(chest)) {
				this.walk(chest);
				return;
			}
			this.soldier.openChest(container, chest);
			this.soldier.getNavigation().stop();
			this.balance(level, container);
			this.equipHorse(level, chest, container);
			this.sorted = true;
			this.buyer = null;
			this.standAttempt = 0;
			return;
		}
		Villager villager = this.buyer == null ? null : this.findAlive(level, this.buyer);
		if (villager == null || this.findOffer(level, villager, container) == null) {
			villager = this.findBuyer(level, container);
			this.buyer = villager == null ? null : villager.getUUID();
			this.standAttempt = 0;
		}
		if (villager == null) {
			this.skipped.clear();
			this.cooldown = TradingRanges.IDLE_COOLDOWN_TICKS;
			this.idle(chest);
			return;
		}
		if (!VillagerWindow.canTrade(level, villager, this.soldier.getX(), this.soldier.getY(), this.soldier.getZ())) {
			this.approach(level, villager);
			return;
		}
		this.soldier.getNavigation().stop();
		this.soldier.getLookControl().setLookAt(villager, 30.0F, 30.0F);
		if (this.buy(level, villager, container)) {
			this.sorted = false;
			this.pause = 20;
			this.buyer = null;
		} else {
			this.skipped.add(villager.getUUID());
			this.buyer = null;
		}
	}

	private boolean ready() {
		if (!(this.soldier.level() instanceof ServerLevel level) || this.soldier.chestPos() == null || !level.isLoaded(this.soldier.chestPos())) {
			return false;
		}
		if (!(level.getBlockEntity(this.soldier.chestPos()) instanceof SoldierChestBlockEntity chest)) {
			return false;
		}
		if (this.soldier.alerting()) {
			return false;
		}
		return chest.campCompleted() && chest.order() == SoldierOrder.REST && CampSite.isComplete(level, this.soldier.chestPos());
	}

	private void hitch(ServerLevel level, BlockPos chest) {
		if (!(this.soldier.getVehicle() instanceof AbstractHorse horse)) {
			this.soldier.stopRiding();
			return;
		}
		BlockPos post = CampSite.hitch(chest);
		double distance = horse.distanceToSqr(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
		if (distance > 6.0) {
			double speed = SoldierDutyGoal.riddenSpeed(horse);
			if (!horse.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, speed)) {
				horse.getMoveControl().setWantedPosition(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, speed);
			}
			return;
		}
		horse.getNavigation().stop();
		if (level.getBlockState(post).is(BlockTags.WOODEN_FENCES)) {
			LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, post);
			if (knot != null) {
				horse.setLeashedTo(knot, true);
			}
		}
		this.soldier.stopRiding();
	}

	private void balance(ServerLevel level, Container chest) {
		this.dump(level, chest, this.soldier.getInventory());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.soldier.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			this.soldier.setItemSlot(slot, ItemStack.EMPTY);
			this.keep(level, chest, stack);
		}
		this.pull(level, chest, CampSite::edible, FOOD_ON_BODY);
		this.pull(level, chest, stack -> stack.is(Items.EMERALD), EMERALDS_ON_BODY);
	}

	private void dump(ServerLevel level, Container chest, SimpleContainer inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			inventory.setItem(slot, ItemStack.EMPTY);
			this.keep(level, chest, stack);
		}
	}

	private void keep(ServerLevel level, Container chest, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack leftover = ChestStash.insert(chest, stack);
		leftover = this.soldier.getInventory().addItem(leftover);
		if (!leftover.isEmpty()) {
			this.soldier.spawnAtLocation(level, leftover);
		}
	}

	private void pull(ServerLevel level, Container chest, Predicate<ItemStack> test, int target) {
		int have = this.count(this.soldier.getInventory(), test) + this.countEquipment(test);
		while (have < target) {
			ItemStack stack = ChestStash.extractMatching(chest, test);
			if (stack.isEmpty()) {
				return;
			}
			int need = target - have;
			ItemStack extra = ItemStack.EMPTY;
			if (stack.getCount() > need) {
				extra = stack.split(stack.getCount() - need);
			}
			int taken = stack.getCount();
			ItemStack leftover = this.soldier.getInventory().addItem(stack);
			have += taken - leftover.getCount();
			if (!leftover.isEmpty()) {
				ChestStash.insert(chest, leftover);
			}
			if (!extra.isEmpty()) {
				ChestStash.insert(chest, extra);
			}
			if (taken == leftover.getCount()) {
				return;
			}
		}
	}

	private boolean needsTack(ServerLevel level, BlockPos chestPos, Container chest) {
		if (!(this.hitchedHorse(level, chestPos) instanceof AbstractHorse horse)) {
			return false;
		}
		if (horse.getItemBySlot(EquipmentSlot.SADDLE).isEmpty() && this.owned(level, chest, stack -> stack.is(Items.SADDLE))) {
			return true;
		}
		return horse.getItemBySlot(EquipmentSlot.BODY).isEmpty() && this.owned(level, chest, SoldierRestGoal::horseArmor);
	}

	private void equipHorse(ServerLevel level, BlockPos chestPos, Container chest) {
		if (!(this.hitchedHorse(level, chestPos) instanceof AbstractHorse horse)) {
			return;
		}
		if (horse.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) {
			this.equip(level, chest, horse, EquipmentSlot.SADDLE, stack -> stack.is(Items.SADDLE));
		}
		if (horse.getItemBySlot(EquipmentSlot.BODY).isEmpty()) {
			this.equip(level, chest, horse, EquipmentSlot.BODY, SoldierRestGoal::horseArmor);
		}
	}

	private void equip(ServerLevel level, Container chest, AbstractHorse horse, EquipmentSlot slot, Predicate<ItemStack> test) {
		ItemStack stack = ChestStash.extractMatching(chest, test);
		if (stack.isEmpty()) {
			stack = ChestStash.extractMatching(this.soldier.getInventory(), test);
		}
		if (stack.isEmpty()) {
			return;
		}
		horse.setItemSlot(slot, stack);
		if (!test.test(horse.getItemBySlot(slot))) {
			this.keep(level, chest, stack);
		}
	}

	@Nullable
	private AbstractHorse hitchedHorse(ServerLevel level, BlockPos chest) {
		BlockPos post = CampSite.hitch(chest);
		AABB area = new AABB(post).inflate(8.0);
		List<AbstractHorse> horses = level.getEntitiesOfClass(AbstractHorse.class, area, horse -> {
			if (!(horse.getLeashHolder() instanceof LeashFenceKnotEntity knot)) {
				return false;
			}
			return post.equals(knot.blockPosition()) || post.equals(knot.blockPosition().below());
		});
		return horses.isEmpty() ? null : horses.getFirst();
	}

	@Nullable
	private Villager findBuyer(ServerLevel level, Container chest) {
		AABB area = this.soldier.getBoundingBox().inflate(TradingRanges.VILLAGER_SEARCH, TradingRanges.VILLAGER_SEARCH_VERTICAL, TradingRanges.VILLAGER_SEARCH);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, villager -> this.canBuyFrom(level, villager, chest));
		villagers.sort((left, right) -> Double.compare(this.soldier.distanceToSqr(left), this.soldier.distanceToSqr(right)));
		return villagers.isEmpty() ? null : villagers.getFirst();
	}

	private boolean canBuyFrom(ServerLevel level, Villager villager, Container chest) {
		if (villager instanceof Soldier || !villager.isAlive() || villager.isBaby() || villager.isSleeping() || villager.isRemoved()) {
			return false;
		}
		if (villager.getTradingPlayer() != null || this.skipped.contains(villager.getUUID())) {
			return false;
		}
		return this.findOffer(level, villager, chest) != null;
	}

	@Nullable
	private Villager findAlive(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof Villager villager && villager.isAlive() ? villager : null;
	}

	@Nullable
	private MerchantOffer findOffer(ServerLevel level, Villager villager, Container chest) {
		MerchantOffers offers = villager.getOffers();
		for (MerchantOffer offer : offers) {
			if (offer.isOutOfStock() || !this.wanted(level, chest, offer.getResult())) {
				continue;
			}
			ItemStack costA = offer.getCostA();
			if (costA.isEmpty() || !costA.is(Items.EMERALD) || this.countAll(level, chest, stack -> stack.is(Items.EMERALD)) < costA.getCount()) {
				continue;
			}
			ItemStack costB = offer.getCostB();
			if (!costB.isEmpty() && this.countAll(level, chest, stack -> ItemStack.isSameItemSameComponents(stack, costB)) < costB.getCount()) {
				continue;
			}
			return offer;
		}
		return null;
	}

	private boolean buy(ServerLevel level, Villager villager, Container chest) {
		MerchantOffer offer = this.findOffer(level, villager, chest);
		if (offer == null) {
			return false;
		}
		ItemStack payment = this.take(chest, Items.EMERALD, offer.getCostA().getCount(), null);
		ItemStack second = ItemStack.EMPTY;
		ItemStack costB = offer.getCostB();
		if (!costB.isEmpty()) {
			second = this.take(chest, costB.getItem(), costB.getCount(), costB);
			if (second.getCount() < costB.getCount()) {
				this.keep(level, chest, payment);
				this.keep(level, chest, second);
				return false;
			}
		}
		if (payment.getCount() < offer.getCostA().getCount() || !offer.satisfiedBy(payment, second) || !offer.take(payment, second)) {
			this.keep(level, chest, payment);
			this.keep(level, chest, second);
			return false;
		}
		this.keep(level, chest, payment);
		this.keep(level, chest, second);
		villager.notifyTrade(offer);
		this.keep(level, chest, offer.assemble());
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9F, 1.0F);
		return true;
	}

	private ItemStack take(Container chest, Item item, int amount, @Nullable ItemStack exact) {
		Predicate<ItemStack> test = exact == null ? stack -> stack.is(item) : stack -> ItemStack.isSameItemSameComponents(stack, exact);
		ItemStack taken = this.takeFrom(this.soldier.getInventory(), test, amount);
		if (taken.getCount() >= amount) {
			return taken;
		}
		ItemStack more = this.takeFrom(chest, test, amount - taken.getCount());
		if (taken.isEmpty()) {
			return more;
		}
		taken.grow(more.getCount());
		return taken;
	}

	private ItemStack takeFrom(@Nullable Container container, Predicate<ItemStack> test, int amount) {
		if (container == null || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = ItemStack.EMPTY;
		for (int slot = 0; slot < container.getContainerSize() && (taken.isEmpty() || taken.getCount() < amount); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!test.test(stack)) {
				continue;
			}
			int move = Math.min(amount - taken.getCount(), stack.getCount());
			if (taken.isEmpty()) {
				taken = stack.split(move);
			} else {
				stack.shrink(move);
				taken.grow(move);
			}
			container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}
		if (!taken.isEmpty()) {
			container.setChanged();
		}
		return taken;
	}

	private boolean wanted(ServerLevel level, Container chest, ItemStack result) {
		if (result.isEmpty()) {
			return false;
		}
		if (CampSite.edible(result)) {
			return this.countAll(level, chest, CampSite::edible) < FOOD_ON_BODY;
		}
		if (result.is(Items.SADDLE)) {
			return !this.owned(level, chest, stack -> stack.is(Items.SADDLE));
		}
		if (horseArmor(result)) {
			return !this.owned(level, chest, SoldierRestGoal::horseArmor);
		}
		if (result.is(ItemTags.ARROWS)) {
			return this.countAll(level, chest, stack -> stack.is(ItemTags.ARROWS)) < ARROW_TARGET;
		}
		if (result.is(Items.BOW)) {
			return this.better(result, this.best(level, chest, stack -> stack.is(Items.BOW)));
		}
		if (result.is(Items.CROSSBOW)) {
			return this.better(result, this.best(level, chest, stack -> stack.is(Items.CROSSBOW)));
		}
		if (this.melee(result)) {
			return this.better(result, this.best(level, chest, this::melee));
		}
		EquipmentSlot slot = this.armorSlot(result);
		if (slot != null) {
			return this.better(result, this.best(level, chest, stack -> slot == this.armorSlot(stack)));
		}
		return false;
	}

	private boolean better(ItemStack candidate, @Nullable ItemStack current) {
		if (current == null || current.isEmpty()) {
			return true;
		}
		int left = this.tier(candidate);
		int right = this.tier(current);
		if (left != right) {
			return left > right;
		}
		return this.enchanted(candidate) && !this.enchanted(current);
	}

	@Nullable
	private ItemStack best(ServerLevel level, Container chest, Predicate<ItemStack> test) {
		ItemStack best = ItemStack.EMPTY;
		for (ItemStack stack : this.stacks(level, chest)) {
			if (!test.test(stack) || !this.better(stack, best)) {
				continue;
			}
			best = stack;
		}
		return best.isEmpty() ? null : best;
	}

	private boolean owned(ServerLevel level, Container chest, Predicate<ItemStack> test) {
		if (this.countAll(level, chest, test) > 0) {
			return true;
		}
		AbstractHorse horse = this.soldier.chestPos() == null ? null : this.hitchedHorse(level, this.soldier.chestPos());
		if (horse == null) {
			return false;
		}
		return test.test(horse.getItemBySlot(EquipmentSlot.SADDLE)) || test.test(horse.getItemBySlot(EquipmentSlot.BODY));
	}

	private List<ItemStack> stacks(ServerLevel level, Container chest) {
		java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
		if (chest != null) {
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				stacks.add(chest.getItem(slot));
			}
		}
		for (int slot = 0; slot < this.soldier.getInventory().getContainerSize(); slot++) {
			stacks.add(this.soldier.getInventory().getItem(slot));
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			stacks.add(this.soldier.getItemBySlot(slot));
		}
		return stacks;
	}

	private int countAll(ServerLevel level, Container chest, Predicate<ItemStack> test) {
		int total = this.count(chest, test) + this.count(this.soldier.getInventory(), test) + this.countEquipment(test);
		AbstractHorse horse = this.soldier.chestPos() == null ? null : this.hitchedHorse(level, this.soldier.chestPos());
		if (horse != null) {
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.SADDLE, EquipmentSlot.BODY}) {
				ItemStack stack = horse.getItemBySlot(slot);
				if (test.test(stack)) {
					total += stack.getCount();
				}
			}
		}
		return total;
	}

	private int count(@Nullable Container container, Predicate<ItemStack> test) {
		if (container == null) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private int countEquipment(Predicate<ItemStack> test) {
		int total = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.soldier.getItemBySlot(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private boolean melee(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS);
	}

	@Nullable
	private EquipmentSlot armorSlot(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || !equippable.allowedEntities().isEmpty()) {
			return null;
		}
		EquipmentSlot slot = equippable.slot();
		if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
			return slot;
		}
		return null;
	}

	private static boolean horseArmor(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.BODY) {
			return false;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("horse_armor");
	}

	private int tier(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.contains("netherite")) {
			return 6;
		}
		if (path.contains("diamond")) {
			return 5;
		}
		if (path.contains("iron")) {
			return 4;
		}
		if (path.contains("copper")) {
			return 3;
		}
		if (path.contains("chainmail") || path.contains("stone")) {
			return 2;
		}
		return 1;
	}

	private boolean enchanted(ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		return enchantments != null && !enchantments.isEmpty();
	}

	private void approach(ServerLevel level, Villager villager) {
		List<BlockPos> stands = VillagerWindow.stands(level, villager, this.soldier);
		if (stands.isEmpty() || this.standAttempt >= stands.size()) {
			this.skipped.add(villager.getUUID());
			this.buyer = null;
			this.standAttempt = 0;
			return;
		}
		BlockPos stand = stands.get(this.standAttempt);
		if (!this.soldier.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) {
			this.standAttempt++;
		}
	}

	private void idle(BlockPos chest) {
		if (!this.near(chest)) {
			this.walk(chest);
			return;
		}
		this.soldier.getNavigation().stop();
		this.soldier.closeChest();
	}

	private void walk(BlockPos pos) {
		this.soldier.closeChest();
		if (this.soldier.tickCount % 15 != 0 && !this.soldier.getNavigation().isDone()) {
			return;
		}
		this.soldier.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
	}

	private boolean near(BlockPos pos) {
		double dx = this.soldier.getX() - (pos.getX() + 0.5);
		double dz = this.soldier.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= 9.0 && Math.abs(this.soldier.getY() - pos.getY()) <= 2.0;
	}
}
