package com.tradinggolem.entity;

import com.tradinggolem.block.SoldierChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Leaves camp for patrol, follow, or an alert. Takes the best gear first.
 * Retreat, mounted shooting, and the spear charge come later.
 */
public final class SoldierDutyGoal extends Goal {
	static final double ALERT_RANGE = 12.0;
	private static final double PATROL_RADIUS = 20.0;
	private static final double PATROL_SIGHT = 12.0;
	private static final double PATROL_LEASH = 32.0;
	private static final double FOLLOW_ATTACK_RANGE = 7.0;
	private static final double FOLLOW_DEFEND_LEASH = 32.0;
	private static final double FOLLOW_GAP = 4.0;
	private static final double FREE_MELEE = 4.0;
	private static final double BOW_RANGE = 15.0;
	private static final int ARROW_TARGET = 64;

	private final Soldier soldier;
	private boolean stocked;
	@Nullable
	private SoldierOrder stockedOrder;
	private int waypoint;
	private int patrolTicks;
	private int attackCooldown;
	private boolean mountSkipped;
	@Nullable
	private UUID focus;

	public SoldierDutyGoal(Soldier soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	static boolean threatNear(ServerLevel level, BlockPos pos, double radius) {
		return nearestThreat(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius) != null;
	}

	@Override
	public boolean canUse() {
		return this.active();
	}

	@Override
	public boolean canContinueToUse() {
		return this.active();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.stocked = false;
		this.stockedOrder = null;
		this.focus = null;
	}

	@Override
	public void stop() {
		this.soldier.closeChest();
		this.soldier.stopUsingItem();
		this.soldier.setTarget(null);
		this.halt();
		this.stocked = false;
		this.stockedOrder = null;
		this.focus = null;
		this.patrolTicks = 0;
		this.attackCooldown = 0;
		this.mountSkipped = false;
	}

	@Override
	public void tick() {
		if (!(this.soldier.level() instanceof ServerLevel level) || this.soldier.chestPos() == null || !level.isLoaded(this.soldier.chestPos())) {
			return;
		}
		if (!(level.getBlockEntity(this.soldier.chestPos()) instanceof SoldierChestBlockEntity chest)) {
			return;
		}
		if (this.soldier.isSleeping()) {
			this.soldier.stopSleeping();
		}
		BlockPos chestPos = this.soldier.chestPos();
		SoldierOrder order = chest.order();
		if (this.stockedOrder != order) {
			this.stockedOrder = order;
			this.stocked = false;
			this.focus = null;
			this.mountSkipped = false;
		}
		if (this.following(order) && this.lord(level, chest) == null) {
			this.soldier.stopUsingItem();
			this.soldier.setTarget(null);
			this.halt();
			return;
		}
		Container container = level.getBlockEntity(chestPos) instanceof Container found ? found : null;
		if (!this.stocked) {
			if (container == null || !this.stock(level, chestPos, container)) {
				return;
			}
			this.stocked = true;
		}
		if (!this.mountReady(level, chestPos, chest.ride())) {
			this.adjustMount(level, chestPos, chest.ride());
			return;
		}
		this.act(level, chestPos, chest);
	}

	private boolean active() {
		if (!(this.soldier.level() instanceof ServerLevel level) || this.soldier.chestPos() == null || !level.isLoaded(this.soldier.chestPos())) {
			return false;
		}
		if (!(level.getBlockEntity(this.soldier.chestPos()) instanceof SoldierChestBlockEntity chest) || !chest.campCompleted()) {
			return false;
		}
		if (this.soldier.alerting()) {
			return true;
		}
		SoldierOrder order = chest.order();
		return order == SoldierOrder.PATROL || order == SoldierOrder.FOLLOW_DEFEND || order == SoldierOrder.FOLLOW_ATTACK;
	}

	private void act(ServerLevel level, BlockPos chestPos, SoldierChestBlockEntity chest) {
		SoldierOrder order = chest.order();
		if (this.soldier.alerting() && (order == SoldierOrder.REST || order == SoldierOrder.PATROL)) {
			LivingEntity threat = this.sticky(level, chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5, ALERT_RANGE);
			if (threat != null) {
				this.combat(level, threat, chest.weaponMode());
				return;
			}
			if (order == SoldierOrder.REST) {
				this.soldier.stopUsingItem();
				this.soldier.setTarget(null);
				this.halt();
				return;
			}
		}
		switch (order) {
			case PATROL -> this.patrol(level, chestPos, chest.weaponMode());
			case FOLLOW_DEFEND -> this.follow(level, chest, true);
			case FOLLOW_ATTACK -> this.follow(level, chest, false);
			default -> {
				this.soldier.stopUsingItem();
				this.soldier.setTarget(null);
				this.halt();
			}
		}
	}

	private void patrol(ServerLevel level, BlockPos chest, SoldierWeaponMode mode) {
		LivingEntity threat = this.sticky(level, this.soldier.getX(), this.soldier.getY(), this.soldier.getZ(), PATROL_SIGHT);
		if (threat != null && threat.distanceToSqr(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5) <= PATROL_LEASH * PATROL_LEASH) {
			this.combat(level, threat, mode);
			return;
		}
		this.focus = null;
		this.soldier.setTarget(null);
		this.soldier.stopUsingItem();
		double angle = this.waypoint * (Math.PI / 4.0);
		double x = chest.getX() + 0.5 + Math.cos(angle) * PATROL_RADIUS;
		double z = chest.getZ() + 0.5 + Math.sin(angle) * PATROL_RADIUS;
		double dx = this.soldier.getX() - x;
		double dz = this.soldier.getZ() - z;
		if (dx * dx + dz * dz <= 4.0 || this.patrolTicks > 160) {
			this.waypoint = (this.waypoint + 1) % 8;
			this.patrolTicks = 0;
			return;
		}
		this.patrolTicks++;
		this.walkTo(x, chest.getY(), z);
	}

	private void follow(ServerLevel level, SoldierChestBlockEntity chest, boolean defend) {
		ServerPlayer lord = this.lord(level, chest);
		if (lord == null) {
			this.halt();
			return;
		}
		LivingEntity target = defend
			? this.defendTarget(lord)
			: this.sticky(level, lord.getX(), lord.getY(), lord.getZ(), FOLLOW_ATTACK_RANGE);
		if (target != null) {
			this.combat(level, target, chest.weaponMode());
			return;
		}
		this.soldier.setTarget(null);
		this.soldier.stopUsingItem();
		if (this.soldier.distanceToSqr(lord) > FOLLOW_GAP * FOLLOW_GAP) {
			this.walkTo(lord.getX(), lord.getY(), lord.getZ());
			return;
		}
		this.halt();
		this.soldier.getLookControl().setLookAt(lord, 30.0F, 30.0F);
	}

	@Nullable
	private LivingEntity defendTarget(ServerPlayer lord) {
		LivingEntity target = lord.getLastHurtMob();
		if (!this.canAssist(lord, target)) {
			this.focus = null;
			return null;
		}
		boolean engaged = target.getUUID().equals(this.focus);
		int age = lord.tickCount - lord.getLastHurtMobTimestamp();
		if (!engaged && age > 100) {
			return null;
		}
		if (target.distanceToSqr(lord) > FOLLOW_DEFEND_LEASH * FOLLOW_DEFEND_LEASH) {
			this.focus = null;
			return null;
		}
		this.focus = target.getUUID();
		return target;
	}

	private boolean canAssist(ServerPlayer lord, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target instanceof Creeper) {
			return false;
		}
		return target != lord && target != this.soldier && target != this.soldier.getVehicle();
	}

	private void combat(ServerLevel level, LivingEntity target, SoldierWeaponMode mode) {
		this.soldier.setTarget(target);
		this.soldier.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean ranged = this.wantsRanged(mode, target);
		this.arm(ranged);
		if (ranged) {
			this.shoot(level, target);
			return;
		}
		this.soldier.stopUsingItem();
		this.melee(level, target);
	}

	private boolean wantsRanged(SoldierWeaponMode mode, LivingEntity target) {
		if (this.bestCarried(this::isRanged) == null || this.arrowStack() == null) {
			return false;
		}
		if (mode == SoldierWeaponMode.MELEE) {
			return false;
		}
		if (mode == SoldierWeaponMode.RANGED) {
			return true;
		}
		return this.soldier.distanceTo(target) > FREE_MELEE;
	}

	private void melee(ServerLevel level, LivingEntity target) {
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}
		double reach = this.soldier.isPassenger() ? 3.5 : 2.5;
		if (this.soldier.distanceToSqr(target) > reach * reach) {
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return;
		}
		this.halt();
		if (this.attackCooldown > 0) {
			return;
		}
		this.soldier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		this.soldier.doHurtTarget(level, target);
		this.attackCooldown = 20;
	}

	private void shoot(ServerLevel level, LivingEntity target) {
		boolean sees = this.soldier.getSensing().hasLineOfSight(target);
		if (this.soldier.distanceTo(target) > BOW_RANGE || !sees) {
			this.soldier.stopUsingItem();
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return;
		}
		this.halt();
		ItemStack weapon = this.soldier.getMainHandItem();
		if (!this.isRanged(weapon)) {
			return;
		}
		int draw = weapon.is(Items.CROSSBOW) ? CrossbowItem.getChargeDuration(weapon, this.soldier) : 20;
		if (!this.soldier.isUsingItem()) {
			this.soldier.startUsingItem(InteractionHand.MAIN_HAND);
		}
		if (this.soldier.getTicksUsingItem() < Math.max(1, draw)) {
			return;
		}
		this.fire(level, target, weapon);
		this.soldier.stopUsingItem();
	}

	private void fire(ServerLevel level, LivingEntity target, ItemStack weapon) {
		ItemStack ammo = this.arrowStack();
		if (ammo == null || ammo.isEmpty()) {
			return;
		}
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this.soldier, ammo.copyWithCount(1), BowItem.getPowerForTime(20), weapon);
		double dx = target.getX() - this.soldier.getX();
		double dy = target.getY(0.3333333333333333) - arrow.getY();
		double dz = target.getZ() - this.soldier.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		Projectile.spawnProjectileUsingShoot(arrow, level, weapon, dx, dy + horiz * 0.2, dz, 1.6F, 1.0F);
		ammo.shrink(1);
		SoundEvent sound = weapon.is(Items.CROSSBOW) ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT;
		this.soldier.playSound(sound, 1.0F, 1.0F);
	}

	private void arm(boolean ranged) {
		ItemStack weapon = ranged ? this.bestCarried(this::isRanged) : this.bestCarried(this::isMelee);
		if (weapon == null) {
			weapon = ranged ? this.bestCarried(this::isMelee) : this.bestCarried(this::isRanged);
		}
		if (weapon != null) {
			this.hold(weapon);
		}
	}

	private void hold(ItemStack wanted) {
		if (this.soldier.getMainHandItem() == wanted) {
			return;
		}
		SimpleContainer inventory = this.soldier.getInventory();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot == EquipmentSlot.MAINHAND || this.soldier.getItemBySlot(slot) != wanted) {
				continue;
			}
			this.soldier.setItemSlot(slot, ItemStack.EMPTY);
			break;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot) != wanted) {
				continue;
			}
			inventory.setItem(slot, ItemStack.EMPTY);
			break;
		}
		ItemStack main = this.soldier.getMainHandItem();
		if (!main.isEmpty()) {
			ItemStack leftover = inventory.addItem(main);
			if (!leftover.isEmpty()) {
				this.soldier.setItemSlot(EquipmentSlot.MAINHAND, leftover);
				ItemStack still = inventory.addItem(wanted);
				if (!still.isEmpty() && this.soldier.level() instanceof ServerLevel level) {
					this.soldier.spawnAtLocation(level, still);
				}
				return;
			}
		}
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		this.soldier.setItemSlot(EquipmentSlot.MAINHAND, wanted);
	}

	private boolean stock(ServerLevel level, BlockPos chestPos, Container chest) {
		if (!this.near(chestPos)) {
			this.walkTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5);
			return false;
		}
		this.soldier.openChest(chest, chestPos);
		this.halt();
		this.takeBest(level, chest, this::isMelee, null);
		this.takeBest(level, chest, this::isRanged, null);
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			EquipmentSlot piece = slot;
			this.takeBest(level, chest, stack -> piece == this.armorSlot(stack), piece);
		}
		if (this.bestCarried(this::isRanged) != null) {
			this.pullArrows(level, chest);
		}
		this.soldier.closeChest();
		return true;
	}

	private void takeBest(ServerLevel level, Container chest, Predicate<ItemStack> test, @Nullable EquipmentSlot destination) {
		Found best = this.findBest(chest, test);
		if (best == null) {
			return;
		}
		if (destination != null) {
			if (best.equipment == destination) {
				return;
			}
			ItemStack wearing = this.soldier.getItemBySlot(destination);
			ItemStack taken = this.detach(best);
			if (!wearing.isEmpty()) {
				this.soldier.setItemSlot(destination, ItemStack.EMPTY);
				this.keep(level, chest, wearing);
			}
			this.soldier.setItemSlot(destination, taken);
			return;
		}
		if (best.container == null || best.container == this.soldier.getInventory()) {
			return;
		}
		ItemStack taken = this.detach(best);
		ItemStack leftover = this.soldier.getInventory().addItem(taken);
		if (leftover.isEmpty()) {
			return;
		}
		if (this.soldier.getMainHandItem().isEmpty()) {
			this.soldier.setItemSlot(EquipmentSlot.MAINHAND, leftover);
			return;
		}
		this.keep(level, chest, leftover);
	}

	@Nullable
	private Found findBest(Container chest, Predicate<ItemStack> test) {
		Found best = this.consider(null, chest, test);
		best = this.consider(best, this.soldier.getInventory(), test);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.soldier.getItemBySlot(slot);
			if (!test.test(stack) || (best != null && !this.better(stack, best.stack))) {
				continue;
			}
			best = new Found(stack, null, -1, slot);
		}
		return best;
	}

	private Found consider(@Nullable Found best, @Nullable Container container, Predicate<ItemStack> test) {
		if (container == null) {
			return best;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!test.test(stack) || (best != null && !this.better(stack, best.stack))) {
				continue;
			}
			best = new Found(stack, container, slot, null);
		}
		return best;
	}

	private ItemStack detach(Found found) {
		if (found.container != null) {
			ItemStack taken = found.container.getItem(found.slot);
			found.container.setItem(found.slot, ItemStack.EMPTY);
			found.container.setChanged();
			return taken;
		}
		ItemStack taken = this.soldier.getItemBySlot(found.equipment);
		this.soldier.setItemSlot(found.equipment, ItemStack.EMPTY);
		return taken;
	}

	private void pullArrows(ServerLevel level, Container chest) {
		int have = this.countBody(stack -> stack.is(ItemTags.ARROWS));
		while (have < ARROW_TARGET) {
			ItemStack stack = ChestStash.extractMatching(chest, candidate -> candidate.is(ItemTags.ARROWS));
			if (stack.isEmpty()) {
				return;
			}
			int need = ARROW_TARGET - have;
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

	@Nullable
	private ItemStack bestCarried(Predicate<ItemStack> test) {
		ItemStack best = ItemStack.EMPTY;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.soldier.getItemBySlot(slot);
			if (test.test(stack) && this.better(stack, best)) {
				best = stack;
			}
		}
		SimpleContainer inventory = this.soldier.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (test.test(stack) && this.better(stack, best)) {
				best = stack;
			}
		}
		return best.isEmpty() ? null : best;
	}

	@Nullable
	private ItemStack arrowStack() {
		ItemStack main = this.soldier.getMainHandItem();
		if (main.is(ItemTags.ARROWS)) {
			return main;
		}
		ItemStack off = this.soldier.getOffhandItem();
		if (off.is(ItemTags.ARROWS)) {
			return off;
		}
		SimpleContainer inventory = this.soldier.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(ItemTags.ARROWS)) {
				return stack;
			}
		}
		return null;
	}

	private int countBody(Predicate<ItemStack> test) {
		int total = 0;
		SimpleContainer inventory = this.soldier.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.soldier.getItemBySlot(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private boolean mountReady(ServerLevel level, BlockPos chestPos, boolean ride) {
		if (!ride) {
			this.mountSkipped = false;
			return !this.soldier.isPassenger();
		}
		if (this.mountSkipped) {
			return !this.soldier.isPassenger();
		}
		AbstractHorse saddled = this.saddledHitched(level, chestPos);
		if (saddled != null) {
			return this.soldier.getVehicle() == saddled;
		}
		if (this.soldier.getVehicle() instanceof AbstractHorse ridden && !ridden.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) {
			return true;
		}
		return !this.soldier.isPassenger();
	}

	private void adjustMount(ServerLevel level, BlockPos chestPos, boolean ride) {
		if (!ride) {
			this.mountSkipped = false;
			if (this.soldier.isPassenger()) {
				this.soldier.stopRiding();
			}
			return;
		}
		AbstractHorse horse = this.saddledHitched(level, chestPos);
		if (horse == null) {
			if (this.soldier.isPassenger() && !(this.soldier.getVehicle() instanceof AbstractHorse ridden && !ridden.getItemBySlot(EquipmentSlot.SADDLE).isEmpty())) {
				this.soldier.stopRiding();
			}
			return;
		}
		if (this.soldier.getVehicle() == horse) {
			return;
		}
		if (this.soldier.isPassenger()) {
			this.soldier.stopRiding();
		}
		if (this.soldier.distanceToSqr(horse) > 4.0) {
			this.walkTo(horse.getX(), horse.getY(), horse.getZ());
			return;
		}
		this.halt();
		if (!this.soldier.startRiding(horse)) {
			this.mountSkipped = true;
			return;
		}
		horse.removeLeash();
	}

	@Nullable
	private AbstractHorse saddledHitched(ServerLevel level, BlockPos chest) {
		AbstractHorse horse = this.hitchedHorse(level, chest);
		if (horse == null || horse.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) {
			return null;
		}
		return horse;
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
	private LivingEntity sticky(ServerLevel level, double x, double y, double z, double radius) {
		LivingEntity current = this.focused(level);
		if (current != null && isThreat(current) && current.distanceToSqr(x, y, z) <= radius * radius) {
			return current;
		}
		LivingEntity found = nearestThreat(level, x, y, z, radius);
		this.focus = found == null ? null : found.getUUID();
		return found;
	}

	@Nullable
	private LivingEntity focused(ServerLevel level) {
		if (this.focus == null || !(level.getEntity(this.focus) instanceof LivingEntity living) || !living.isAlive()) {
			return null;
		}
		return living;
	}

	@Nullable
	private static LivingEntity nearestThreat(ServerLevel level, double x, double y, double z, double radius) {
		AABB area = new AABB(x, y, z, x, y, z).inflate(radius);
		LivingEntity nearest = null;
		double best = radius * radius;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, SoldierDutyGoal::isThreat)) {
			double distance = entity.distanceToSqr(x, y, z);
			if (distance > best) {
				continue;
			}
			best = distance;
			nearest = entity;
		}
		return nearest;
	}

	private static boolean isThreat(LivingEntity entity) {
		return entity.isAlive() && entity instanceof Enemy && !(entity instanceof Creeper);
	}

	@Nullable
	private ServerPlayer lord(ServerLevel level, SoldierChestBlockEntity chest) {
		if (chest.lord() == null || level.getServer() == null) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(chest.lord());
		if (player == null || player.level() != level || !player.isAlive()) {
			return null;
		}
		return player;
	}

	private boolean following(SoldierOrder order) {
		return order == SoldierOrder.FOLLOW_DEFEND || order == SoldierOrder.FOLLOW_ATTACK;
	}

	private boolean isMelee(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS);
	}

	private boolean isRanged(ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
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

	private void walkTo(double x, double y, double z) {
		this.soldier.closeChest();
		Mob mover = this.soldier.getVehicle() instanceof Mob mount ? mount : this.soldier;
		if (this.soldier.tickCount % 10 != 0 && !mover.getNavigation().isDone()) {
			return;
		}
		double speed = mover instanceof AbstractHorse horse ? riddenSpeed(horse) : 1.0;
		if (!mover.getNavigation().moveTo(x, y, z, speed)) {
			mover.getMoveControl().setWantedPosition(x, y, z, speed);
		}
	}

	/** Path speed that lands on the same pace as a player riding this horse. */
	static double riddenSpeed(AbstractHorse horse) {
		double speed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
		if (speed < 1.0E-4) {
			return 1.0;
		}
		return 1.0 / Math.sqrt(speed);
	}

	private void halt() {
		this.soldier.getNavigation().stop();
		if (this.soldier.getVehicle() instanceof Mob mount) {
			mount.getNavigation().stop();
		}
	}

	private boolean near(BlockPos pos) {
		double dx = this.soldier.getX() - (pos.getX() + 0.5);
		double dz = this.soldier.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= 9.0 && Math.abs(this.soldier.getY() - pos.getY()) <= 2.0;
	}

	private record Found(ItemStack stack, @Nullable Container container, int slot, @Nullable EquipmentSlot equipment) {
	}
}
