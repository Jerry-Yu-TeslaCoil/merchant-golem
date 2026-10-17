package com.tradinggolem.entity;

import com.tradinggolem.block.SoldierChestBlockEntity;
import com.tradinggolem.mixin.ArrowDamageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Leaves camp for patrol, follow, or an alert. Takes the best gear first.
 * Locked ranged keeps its distance, a mounted archer shoots while backing up,
 * and a mounted spear charges once before settling into melee.
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
	private static final double RANGED_RESUME = 7.0;
	private static final double MOUNTED_REVERSE = 12.0;
	private static final double BOW_RANGE = 15.0;
	private static final double CHARGE_REACH = 4.0;
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
	/** Locked ranged is backing up until the target is 7 blocks away. */
	private boolean backing;
	private boolean chargeLanded;
	@Nullable
	private UUID chargeTarget;
	/** Patrol came back because both weapons were gone. Each kind is tried once. */
	private boolean resupplying;
	private boolean triedMelee;
	private boolean triedRanged;
	private boolean tookFromChest;
	private boolean emeraldsReady;
	private int standAttempt;
	@Nullable
	private UUID resupplyBuyer;

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
		this.backing = false;
		this.chargeLanded = false;
		this.chargeTarget = null;
		this.patrolTicks = 0;
		this.attackCooldown = 0;
		this.mountSkipped = false;
		this.clearResupply();
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
			this.clearResupply();
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
		if (this.soldier.alerting()) {
			if (container != null && !this.grabChestWeapons(level, chestPos, container)) {
				return;
			}
			if (!this.mountReady(level, chestPos, chest.ride())) {
				this.adjustMount(level, chestPos, chest.ride());
				return;
			}
			this.act(level, chestPos, chest);
			return;
		}
		if (!this.mountReady(level, chestPos, chest.ride())) {
			this.adjustMount(level, chestPos, chest.ride());
			return;
		}
		if (order == SoldierOrder.PATROL && container != null && this.needsResupply()) {
			this.resupply(level, chestPos, chest, container);
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
			LivingEntity threat = this.engage(level, chest, chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5, ALERT_RANGE, chest.weaponMode());
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
			case PATROL -> this.patrol(level, chestPos, chest);
			case FOLLOW_DEFEND -> this.follow(level, chest, true);
			case FOLLOW_ATTACK -> this.follow(level, chest, false);
			default -> {
				this.soldier.stopUsingItem();
				this.soldier.setTarget(null);
				this.halt();
			}
		}
	}

	private void patrol(ServerLevel level, BlockPos chestPos, SoldierChestBlockEntity chest) {
		LivingEntity threat = this.engage(level, chest, this.soldier.getX(), this.soldier.getY(), this.soldier.getZ(), PATROL_SIGHT, chest.weaponMode());
		if (threat != null && threat.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5) <= PATROL_LEASH * PATROL_LEASH) {
			this.combat(level, threat, chest.weaponMode());
			return;
		}
		this.focus = null;
		this.soldier.setTarget(null);
		this.soldier.stopUsingItem();
		double angle = this.waypoint * (Math.PI / 4.0);
		double x = chestPos.getX() + 0.5 + Math.cos(angle) * PATROL_RADIUS;
		double z = chestPos.getZ() + 0.5 + Math.sin(angle) * PATROL_RADIUS;
		double dx = this.soldier.getX() - x;
		double dz = this.soldier.getZ() - z;
		if (dx * dx + dz * dz <= 4.0 || this.patrolTicks > 160) {
			this.waypoint = (this.waypoint + 1) % 8;
			this.patrolTicks = 0;
			return;
		}
		this.patrolTicks++;
		this.walkTo(x, chestPos.getY(), z);
	}

	private void follow(ServerLevel level, SoldierChestBlockEntity chest, boolean defend) {
		ServerPlayer lord = this.lord(level, chest);
		if (lord == null) {
			this.halt();
			return;
		}
		LivingEntity target = defend
			? this.defendTarget(lord, chest.weaponMode())
			: this.engage(level, chest, lord.getX(), lord.getY(), lord.getZ(), FOLLOW_ATTACK_RANGE, chest.weaponMode());
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
	private LivingEntity defendTarget(ServerPlayer lord, SoldierWeaponMode mode) {
		LivingEntity target = lord.getLastHurtMob();
		if (target instanceof Creeper creeper) {
			return this.assistCreeper(lord, creeper, mode);
		}
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
		if (target == null || !target.isAlive() || target instanceof Creeper || target instanceof IronGolem) {
			return false;
		}
		return target != lord && target != this.soldier && target != this.soldier.getVehicle();
	}

	@Nullable
	private LivingEntity assistCreeper(ServerPlayer lord, Creeper creeper, SoldierWeaponMode mode) {
		if (!this.mayShootCreeper(mode) || !creeper.isAlive()) {
			this.focus = null;
			return null;
		}
		boolean engaged = creeper.getUUID().equals(this.focus);
		int age = lord.tickCount - lord.getLastHurtMobTimestamp();
		if (!engaged && age > 100) {
			return null;
		}
		if (creeper.distanceToSqr(lord) > FOLLOW_DEFEND_LEASH * FOLLOW_DEFEND_LEASH) {
			this.focus = null;
			return null;
		}
		this.focus = creeper.getUUID();
		return creeper;
	}

	/** Normal monsters first. A creeper counts only after the lord has hit it, and only for a bow. */
	@Nullable
	private LivingEntity engage(ServerLevel level, SoldierChestBlockEntity chest, double x, double y, double z, double radius, SoldierWeaponMode mode) {
		UUID kept = this.focus;
		LivingEntity threat = this.sticky(level, x, y, z, radius);
		if (threat != null) {
			return threat;
		}
		if (this.keptCreeper(level, kept, x, y, z, radius, mode) instanceof Creeper held) {
			return held;
		}
		return this.freshCreeper(level, chest, x, y, z, radius, mode);
	}

	@Nullable
	private Creeper keptCreeper(ServerLevel level, @Nullable UUID id, double x, double y, double z, double radius, SoldierWeaponMode mode) {
		if (id == null || !this.mayShootCreeper(mode) || !(level.getEntity(id) instanceof Creeper creeper) || !creeper.isAlive()) {
			return null;
		}
		if (creeper.distanceToSqr(x, y, z) > radius * radius) {
			return null;
		}
		this.focus = creeper.getUUID();
		return creeper;
	}

	@Nullable
	private Creeper freshCreeper(ServerLevel level, SoldierChestBlockEntity chest, double x, double y, double z, double radius, SoldierWeaponMode mode) {
		ServerPlayer lord = this.lord(level, chest);
		if (lord == null || !this.mayShootCreeper(mode) || !(lord.getLastHurtMob() instanceof Creeper creeper) || !creeper.isAlive()) {
			return null;
		}
		if (lord.tickCount - lord.getLastHurtMobTimestamp() > 100) {
			return null;
		}
		if (creeper.distanceToSqr(x, y, z) > radius * radius) {
			return null;
		}
		this.focus = creeper.getUUID();
		return creeper;
	}

	private boolean mayShootCreeper(SoldierWeaponMode mode) {
		return mode != SoldierWeaponMode.MELEE && this.bestCarried(this::isRanged) != null && this.arrowStack() != null;
	}

	private void combat(ServerLevel level, LivingEntity target, SoldierWeaponMode mode) {
		if (target instanceof IronGolem) {
			this.soldier.setTarget(null);
			this.focus = null;
			this.soldier.stopUsingItem();
			return;
		}
		this.soldier.setTarget(target);
		this.soldier.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.chargeTarget == null || !this.chargeTarget.equals(target.getUUID())) {
			this.chargeTarget = target.getUUID();
			this.chargeLanded = false;
			this.backing = false;
		}
		boolean creeper = target instanceof Creeper;
		if (creeper && !this.mayShootCreeper(mode)) {
			this.soldier.stopUsingItem();
			this.soldier.setTarget(null);
			this.focus = null;
			this.halt();
			return;
		}
		boolean ranged = creeper || this.wantsRanged(mode, target);
		this.arm(ranged);
		if (!ranged) {
			this.backing = false;
			this.melee(level, target);
			return;
		}
		this.lowerShield();
		if (mode == SoldierWeaponMode.RANGED && this.soldier.isPassenger()) {
			this.backing = false;
			this.reverseShoot(level, target);
			return;
		}
		if (mode == SoldierWeaponMode.RANGED && this.keepDistance(target)) {
			return;
		}
		this.shoot(level, target);
	}

	/** Locked ranged backs up after a monster steps inside 4 blocks, and shoots again at 7. */
	private boolean keepDistance(LivingEntity target) {
		double distance = this.soldier.distanceTo(target);
		if (distance < FREE_MELEE) {
			this.backing = true;
		}
		if (!this.backing) {
			return false;
		}
		if (distance < RANGED_RESUME) {
			this.soldier.stopUsingItem();
			this.away(target, 6.0);
			return true;
		}
		this.backing = false;
		return false;
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
		if (this.spearCharge(level, target)) {
			return;
		}
		if (this.soldier.isUsingItem() && this.soldier.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			this.soldier.stopUsingItem();
		}
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}
		double reach = this.soldier.isPassenger() ? 3.5 : 2.5;
		if (this.soldier.distanceToSqr(target) > reach * reach) {
			this.guard();
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return;
		}
		this.halt();
		if (this.attackCooldown > 0) {
			this.guard();
			return;
		}
		this.lowerShield();
		this.soldier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		this.soldier.doHurtTarget(level, target);
		this.attackCooldown = this.soldier.attackInterval();
	}

	/** Offhand shield stays up only while the next swing is still on cooldown. */
	private void guard() {
		if (this.attackCooldown <= 0 || !this.soldier.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			this.lowerShield();
			return;
		}
		if (this.soldier.isUsingItem() && this.soldier.getUsedItemHand() == InteractionHand.OFF_HAND) {
			return;
		}
		if (this.soldier.isUsingItem()) {
			this.soldier.stopUsingItem();
		}
		this.soldier.startUsingItem(InteractionHand.OFF_HAND);
	}

	private void lowerShield() {
		if (this.soldier.isUsingItem() && this.soldier.getUsedItemHand() == InteractionHand.OFF_HAND) {
			this.soldier.stopUsingItem();
		}
	}

	/**
	 * One mounted spear impact. It does not use the spear's charge window: a monster in front, within reach, is hit and shoved.
	 * Later swings are ordinary melee.
	 */
	private boolean spearCharge(ServerLevel level, LivingEntity target) {
		if (!(this.soldier.getVehicle() instanceof AbstractHorse) || !this.soldier.getMainHandItem().is(ItemTags.SPEARS)) {
			return false;
		}
		if (this.chargeLanded) {
			return false;
		}
		if (!this.chargeReach(target)) {
			this.guard();
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return true;
		}
		this.halt();
		this.lowerShield();
		this.soldier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!this.soldier.doHurtTarget(level, target)) {
			return true;
		}
		double dx = this.soldier.getX() - target.getX();
		double dz = this.soldier.getZ() - target.getZ();
		if (dx * dx + dz * dz < 1.0E-4) {
			Vec3 look = this.soldier.getLookAngle();
			dx = -look.x;
			dz = -look.z;
		}
		target.knockback(0.6, dx, dz, this.soldier.damageSources().mobAttack(this.soldier), 0.4F);
		this.chargeLanded = true;
		this.attackCooldown = 10;
		return true;
	}

	private boolean chargeReach(LivingEntity target) {
		double dx = target.getX() - this.soldier.getX();
		double dz = target.getZ() - this.soldier.getZ();
		if (dx * dx + dz * dz > CHARGE_REACH * CHARGE_REACH || Math.abs(target.getY() - this.soldier.getY()) > 2.0) {
			return false;
		}
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 0.5) {
			return true;
		}
		Vec3 look = this.soldier.getLookAngle();
		return (look.x * dx + look.z * dz) / horizontal > 0.3;
	}

	private void shoot(ServerLevel level, LivingEntity target) {
		if (!this.inBowRange(target)) {
			this.soldier.stopUsingItem();
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return;
		}
		this.halt();
		this.release(level, target);
	}

	/** Look stays on the monster. Inside 12 blocks the horse keeps moving away while the bow fires. */
	private void reverseShoot(ServerLevel level, LivingEntity target) {
		if (!this.inBowRange(target)) {
			this.soldier.stopUsingItem();
			this.walkTo(target.getX(), target.getY(), target.getZ());
			return;
		}
		if (this.soldier.distanceTo(target) < MOUNTED_REVERSE) {
			this.away(target, 6.0);
		} else {
			this.halt();
		}
		this.release(level, target);
	}

	private boolean inBowRange(LivingEntity target) {
		return this.soldier.distanceTo(target) <= BOW_RANGE && this.soldier.getSensing().hasLineOfSight(target);
	}

	private void release(ServerLevel level, LivingEntity target) {
		ItemStack weapon = this.soldier.getMainHandItem();
		if (!this.isRanged(weapon)) {
			return;
		}
		if (!this.soldier.isUsingItem()) {
			this.soldier.startUsingItem(InteractionHand.MAIN_HAND);
		}
		if (this.soldier.getTicksUsingItem() < 20) {
			return;
		}
		if (!this.fire(level, target, weapon)) {
			return;
		}
		this.soldier.stopUsingItem();
	}

	private void away(LivingEntity target, double blocks) {
		double dx = this.soldier.getX() - target.getX();
		double dz = this.soldier.getZ() - target.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-4) {
			dx = 1.0;
			dz = 0.0;
			length = 1.0;
		}
		this.walkTo(this.soldier.getX() + dx / length * blocks, this.soldier.getY(), this.soldier.getZ() + dz / length * blocks);
	}

	/** False when the bow stays drawn: no arrow, or another living thing is in the way. */
	private boolean fire(ServerLevel level, LivingEntity target, ItemStack weapon) {
		ItemStack ammo = this.arrowStack();
		if (ammo == null || ammo.isEmpty() || !this.clearShot(target)) {
			return false;
		}
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this.soldier, ammo.copyWithCount(1), BowItem.getPowerForTime(20), weapon);
		double dx = target.getX() - this.soldier.getX();
		double dy = target.getY(0.3333333333333333) - arrow.getY();
		double dz = target.getZ() - this.soldier.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		arrow.setBaseDamage(((ArrowDamageAccess) arrow).tradingGolem$getBaseDamage() + this.soldier.damageBonus());
		Projectile.spawnProjectileUsingShoot(arrow, level, weapon, dx, dy + horiz * 0.2, dz, 1.6F, this.soldier.bowSpread());
		ammo.shrink(1);
		this.soldier.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
		return true;
	}

	/** Hold the shot while any other living thing stands on the line to the target. */
	private boolean clearShot(LivingEntity target) {
		Vec3 from = this.soldier.getEyePosition();
		Vec3 to = new Vec3(target.getX(), target.getY(0.3333333333333333), target.getZ());
		AABB box = this.soldier.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(
			this.soldier,
			from,
			to,
			box,
			entity -> entity instanceof LivingEntity living && living.isAlive() && living != this.soldier.getVehicle(),
			from.distanceToSqr(to)
		);
		return hit == null || hit.getEntity() == target;
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
		this.soldier.stopUsingItem();
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
		this.takeBest(level, chest, this::isShield, EquipmentSlot.OFFHAND);
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
		return stack.is(Items.BOW);
	}

	private boolean isShield(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS);
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

	/** Alert may take spare weapons from the chest, then ride. It does not go shopping. */
	private boolean grabChestWeapons(ServerLevel level, BlockPos chestPos, Container chest) {
		boolean needMelee = this.bestCarried(this::isMelee) == null && this.chestHas(chest, this::isMelee);
		boolean needRanged = this.bestCarried(this::isRanged) == null && this.chestHas(chest, this::isRanged);
		if (!needMelee && !needRanged) {
			return true;
		}
		if (!this.near(chestPos)) {
			this.walkTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5);
			return false;
		}
		this.soldier.openChest(chest, chestPos);
		this.halt();
		if (needMelee) {
			this.takeBest(level, chest, this::isMelee, null);
		}
		if (needRanged) {
			this.takeBest(level, chest, this::isRanged, null);
		}
		if (this.bestCarried(this::isRanged) != null) {
			this.pullArrows(level, chest);
		}
		if (this.bestCarried(this::isShield) == null && this.chestHas(chest, this::isShield)) {
			this.takeBest(level, chest, this::isShield, EquipmentSlot.OFFHAND);
		}
		this.soldier.closeChest();
		return true;
	}

	private boolean chestHas(Container chest, Predicate<ItemStack> test) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (test.test(chest.getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasWeapon() {
		return this.bestCarried(this::isMelee) != null || this.bestCarried(this::isRanged) != null;
	}

	private boolean needsResupply() {
		return this.resupplying || !this.hasWeapon();
	}

	/**
	 * Both weapons are gone. Always try melee, then always try a bow, even if the sword is already in hand.
	 * One weapon is enough to patrol again. If both attempts come back empty, rest.
	 */
	private void resupply(ServerLevel level, BlockPos chestPos, SoldierChestBlockEntity chest, Container container) {
		if (!this.resupplying) {
			this.resupplying = true;
			this.triedMelee = false;
			this.triedRanged = false;
			this.tookFromChest = false;
			this.emeraldsReady = false;
			this.resupplyBuyer = null;
			this.standAttempt = 0;
			this.soldier.setTarget(null);
			this.soldier.stopUsingItem();
		}
		if (!this.triedMelee) {
			this.attemptWeapon(level, chestPos, container, true);
			return;
		}
		if (!this.triedRanged) {
			this.attemptWeapon(level, chestPos, container, false);
			return;
		}
		if (this.hasWeapon()) {
			if (this.bestCarried(this::isRanged) != null && this.near(chestPos)) {
				this.soldier.openChest(container, chestPos);
				this.pullArrows(level, container);
				this.soldier.closeChest();
			}
			this.clearResupply();
			return;
		}
		chest.setOrder(SoldierOrder.REST);
		this.clearResupply();
		this.soldier.stopUsingItem();
		this.soldier.setTarget(null);
		this.halt();
	}

	private void attemptWeapon(ServerLevel level, BlockPos chestPos, Container chest, boolean melee) {
		Predicate<ItemStack> test = melee ? this::isMelee : this::isRanged;
		if (this.bestCarried(test) != null) {
			this.finishAttempt(melee);
			return;
		}
		if (!this.tookFromChest) {
			if (!this.near(chestPos)) {
				this.walkTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5);
				return;
			}
			this.soldier.openChest(chest, chestPos);
			this.halt();
			if (!this.emeraldsReady) {
				this.pullEmeralds(chest);
				this.emeraldsReady = true;
			}
			this.takeBest(level, chest, test, null);
			this.soldier.closeChest();
			this.tookFromChest = true;
			if (this.bestCarried(test) != null) {
				this.finishAttempt(melee);
			}
			return;
		}
		if (this.resupplyBuyer == null) {
			if (!this.near(chestPos)) {
				this.walkTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5);
				return;
			}
			Villager villager = this.findSeller(level, chest, test);
			if (villager == null) {
				this.finishAttempt(melee);
				return;
			}
			this.resupplyBuyer = villager.getUUID();
			this.standAttempt = 0;
		}
		if (!(level.getEntity(this.resupplyBuyer) instanceof Villager villager) || !villager.isAlive()) {
			this.finishAttempt(melee);
			return;
		}
		if (this.findWeaponOffer(level, villager, chest, test) == null) {
			this.finishAttempt(melee);
			return;
		}
		if (!VillagerWindow.canTrade(level, villager, this.soldier.getX(), this.soldier.getY(), this.soldier.getZ())) {
			if (!this.approachSeller(level, villager)) {
				this.finishAttempt(melee);
			}
			return;
		}
		this.buyWeapon(level, villager, chest, test);
		this.finishAttempt(melee);
	}

	private void finishAttempt(boolean melee) {
		if (melee) {
			this.triedMelee = true;
		} else {
			this.triedRanged = true;
		}
		this.tookFromChest = false;
		this.resupplyBuyer = null;
		this.standAttempt = 0;
	}

	private void clearResupply() {
		this.resupplying = false;
		this.triedMelee = false;
		this.triedRanged = false;
		this.tookFromChest = false;
		this.emeraldsReady = false;
		this.resupplyBuyer = null;
		this.standAttempt = 0;
	}

	private void pullEmeralds(Container chest) {
		int have = this.countBody(stack -> stack.is(Items.EMERALD));
		while (have < 64) {
			ItemStack stack = ChestStash.extractMatching(chest, candidate -> candidate.is(Items.EMERALD));
			if (stack.isEmpty()) {
				return;
			}
			int need = 64 - have;
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

	@Nullable
	private Villager findSeller(ServerLevel level, Container chest, Predicate<ItemStack> test) {
		AABB area = this.soldier.getBoundingBox().inflate(TradingRanges.VILLAGER_SEARCH, TradingRanges.VILLAGER_SEARCH_VERTICAL, TradingRanges.VILLAGER_SEARCH);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, villager -> this.canBuyWeapon(level, villager, chest, test));
		villagers.sort((left, right) -> Double.compare(this.soldier.distanceToSqr(left), this.soldier.distanceToSqr(right)));
		return villagers.isEmpty() ? null : villagers.getFirst();
	}

	private boolean canBuyWeapon(ServerLevel level, Villager villager, Container chest, Predicate<ItemStack> test) {
		if (villager instanceof Soldier || !villager.isAlive() || villager.isBaby() || villager.isSleeping() || villager.isRemoved()) {
			return false;
		}
		if (villager.getTradingPlayer() != null) {
			return false;
		}
		return this.findWeaponOffer(level, villager, chest, test) != null;
	}

	@Nullable
	private MerchantOffer findWeaponOffer(ServerLevel level, Villager villager, Container chest, Predicate<ItemStack> test) {
		MerchantOffers offers = villager.getOffers();
		for (MerchantOffer offer : offers) {
			if (offer.isOutOfStock() || !test.test(offer.getResult())) {
				continue;
			}
			ItemStack costA = offer.getCostA();
			if (costA.isEmpty() || !costA.is(Items.EMERALD) || this.countStored(chest, stack -> stack.is(Items.EMERALD)) < costA.getCount()) {
				continue;
			}
			ItemStack costB = offer.getCostB();
			if (!costB.isEmpty() && this.countStored(chest, stack -> ItemStack.isSameItemSameComponents(stack, costB)) < costB.getCount()) {
				continue;
			}
			return offer;
		}
		return null;
	}

	private void buyWeapon(ServerLevel level, Villager villager, Container chest, Predicate<ItemStack> test) {
		MerchantOffer offer = this.findWeaponOffer(level, villager, chest, test);
		if (offer == null) {
			return;
		}
		ItemStack payment = this.takePayment(chest, Items.EMERALD, offer.getCostA().getCount(), null);
		ItemStack second = ItemStack.EMPTY;
		ItemStack costB = offer.getCostB();
		if (!costB.isEmpty()) {
			second = this.takePayment(chest, costB.getItem(), costB.getCount(), costB);
			if (second.getCount() < costB.getCount()) {
				this.keep(level, chest, payment);
				this.keep(level, chest, second);
				return;
			}
		}
		if (payment.getCount() < offer.getCostA().getCount() || !offer.satisfiedBy(payment, second) || !offer.take(payment, second)) {
			this.keep(level, chest, payment);
			this.keep(level, chest, second);
			return;
		}
		this.keep(level, chest, payment);
		this.keep(level, chest, second);
		villager.notifyTrade(offer);
		this.keep(level, chest, offer.assemble());
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9F, 1.0F);
	}

	private ItemStack takePayment(Container chest, Item item, int amount, @Nullable ItemStack exact) {
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

	private int countStored(Container chest, Predicate<ItemStack> test) {
		int total = this.countBody(test);
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (test.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** @return false when no stand is left, so this buy attempt is over */
	private boolean approachSeller(ServerLevel level, Villager villager) {
		List<BlockPos> stands = VillagerWindow.stands(level, villager, this.soldier);
		if (stands.isEmpty() || this.standAttempt >= stands.size()) {
			return false;
		}
		BlockPos stand = stands.get(this.standAttempt);
		if (!this.soldier.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) {
			this.standAttempt++;
		}
		return true;
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
