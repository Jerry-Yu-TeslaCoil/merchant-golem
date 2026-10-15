package com.tradinggolem.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a trading-hall villager can be traded with.
 * The usual cell has blocks around the feet so the villager cannot pathfind,
 * and air (or an open trapdoor) in front of the face so a player can click them.
 * The golem stands in the aisle and must not step into that opening, or it shoves the villager out.
 */
final class VillagerWindow {
	private final Direction face;
	private final boolean locked;
	private final BlockPos opening;

	private VillagerWindow(Direction face, boolean locked, BlockPos opening) {
		this.face = face;
		this.locked = locked;
		this.opening = opening;
	}

	static boolean canTrade(Level level, Villager villager, double x, double y, double z) {
		VillagerWindow window = find(level, villager, x, z);
		if (window == null || !window.locked) {
			double reach = TradingRanges.FREE_REACH;
			return villager.getBoundingBox().distanceToSqr(new Vec3(x, y + TradingRanges.EYE_HEIGHT, z)) <= reach * reach;
		}
		return window.withinReach(x, y, z) && window.inFront(villager, x, z);
	}

	static List<BlockPos> stands(Level level, Villager villager, TradingGolem golem) {
		BlockPos origin = villager.blockPosition();
		VillagerWindow window = find(level, villager, golem.getX(), golem.getZ());
		List<BlockPos> stands = new ArrayList<>();
		if (window != null && window.locked) {
			collectLockedStands(level, villager, origin, window, stands);
		} else {
			collectOpenStands(level, villager, origin, stands);
		}
		stands.sort(Comparator.comparingDouble(pos -> golem.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
		if (stands.size() > 8) {
			return new ArrayList<>(stands.subList(0, 8));
		}
		return stands;
	}

	private static void collectLockedStands(Level level, Villager villager, BlockPos origin, VillagerWindow window, List<BlockPos> stands) {
		Direction face = window.face;
		Direction side = face.getClockWise();
		for (int forward = 1; forward <= 4; forward++) {
			for (int lateral = -2; lateral <= 2; lateral++) {
				for (int dy = -4; dy <= 2; dy++) {
					BlockPos feet = origin.relative(face, forward).relative(side, lateral).offset(0, dy, 0);
					if (wouldEnterCell(origin, face, feet) || !isStandable(level, feet)) {
						continue;
					}
					double x = feet.getX() + 0.5;
					double y = feet.getY();
					double z = feet.getZ() + 0.5;
					if (window.withinReach(x, y, z) && window.inFront(villager, x, z)) {
						stands.add(feet.immutable());
					}
				}
			}
		}
	}

	private static void collectOpenStands(Level level, Villager villager, BlockPos origin, List<BlockPos> stands) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					BlockPos feet = origin.offset(dx, dy, dz);
					if (!isStandable(level, feet)) {
						continue;
					}
					double x = feet.getX() + 0.5;
					double y = feet.getY();
					double z = feet.getZ() + 0.5;
					double reach = TradingRanges.FREE_REACH;
					if (villager.getBoundingBox().distanceToSqr(new Vec3(x, y + TradingRanges.EYE_HEIGHT, z)) <= reach * reach) {
						stands.add(feet.immutable());
					}
				}
			}
		}
	}

	/**
	 * The column in front of a locked villager is the window: solid at the feet, open at the face.
	 * Standing there puts the golem in the hole and knocks the villager loose.
	 * Standing under that lip, or in the aisle beyond it, does not.
	 */
	private static boolean wouldEnterCell(BlockPos origin, Direction face, BlockPos feet) {
		if (feet.getX() == origin.getX() && feet.getZ() == origin.getZ()) {
			return true;
		}
		BlockPos wall = origin.relative(face);
		return feet.getX() == wall.getX() && feet.getZ() == wall.getZ() && feet.getY() >= origin.getY();
	}

	@Nullable
	private static VillagerWindow find(Level level, Villager villager, double fromX, double fromZ) {
		BlockPos origin = villager.blockPosition();
		Direction best = null;
		int bestScore = Integer.MIN_VALUE;
		double bestDistance = Double.MAX_VALUE;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos head = openingBlock(level, origin, direction);
			if (head == null) {
				continue;
			}
			boolean feetBlocked = blocksFeet(level, origin.relative(direction));
			int score = feetBlocked ? 2 : 0;
			if (direction == villager.getDirection()) {
				score++;
			}
			double distance = origin.relative(direction).distToCenterSqr(fromX, origin.getY(), fromZ);
			if (score > bestScore || (score == bestScore && distance < bestDistance)) {
				best = direction;
				bestScore = score;
				bestDistance = distance;
			}
		}
		if (best == null) {
			return null;
		}
		BlockPos opening = openingBlock(level, origin, best);
		if (opening == null) {
			return null;
		}
		return new VillagerWindow(best, blocksFeet(level, origin.relative(best)), opening);
	}

	@Nullable
	private static BlockPos openingBlock(Level level, BlockPos origin, Direction direction) {
		BlockPos head = origin.relative(direction).above();
		if (isFaceOpen(level, head)) {
			return head;
		}
		BlockPos higher = head.above();
		if (isFaceOpen(level, higher)) {
			return higher;
		}
		return null;
	}

	private boolean withinReach(double x, double y, double z) {
		double dx = x - (this.opening.getX() + 0.5);
		double dy = (y + TradingRanges.EYE_HEIGHT) - (this.opening.getY() + 0.5);
		double dz = z - (this.opening.getZ() + 0.5);
		double reach = TradingRanges.WINDOW_REACH;
		return dx * dx + dy * dy + dz * dz <= reach * reach;
	}

	private boolean inFront(Villager villager, double x, double z) {
		double dx = x - villager.getX();
		double dz = z - villager.getZ();
		return dx * this.face.getStepX() + dz * this.face.getStepZ() > 0.4;
	}

	/** Air, signs, and open trapdoors. A player can click the face through these. */
	private static boolean isFaceOpen(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof TrapDoorBlock) {
			return state.getValue(TrapDoorBlock.OPEN);
		}
		return state.getCollisionShape(level, pos).isEmpty();
	}

	/** Full blocks and trapdoors are how a cell keeps the villager from walking out. */
	private static boolean blocksFeet(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		return !state.getCollisionShape(level, pos).isEmpty();
	}

	static boolean isStandable(Level level, BlockPos feet) {
		BlockState state = level.getBlockState(feet);
		if (state.getBlock() instanceof CarpetBlock) {
			return hasSupport(level, feet.below());
		}
		if (!state.getCollisionShape(level, feet).isEmpty()) {
			return false;
		}
		return hasSupport(level, feet.below());
	}

	private static boolean hasSupport(Level level, BlockPos ground) {
		BlockState below = level.getBlockState(ground);
		if (below.getBlock() instanceof TrapDoorBlock) {
			return !below.getValue(TrapDoorBlock.OPEN);
		}
		if (below.getBlock() instanceof CarpetBlock) {
			return false;
		}
		VoxelShape shape = below.getCollisionShape(level, ground);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5;
	}
}
