package com.tradinggolem.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * 箱子放在中心地面方块上。沟、耕地都在箱子下面那一层。
 * 11×11 的边框挖掉，四边正中各留一格当过道；水只放在四个角。
 */
public final class FarmLayout {
	public static final int DITCH_RADIUS = 5;
	public static final int PLOT_RADIUS = 4;

	private FarmLayout() {
	}

	public static BlockPos ground(BlockPos chest) {
		return chest.below();
	}

	public static int chebyshev(BlockPos origin, BlockPos pos) {
		return Math.max(Math.abs(pos.getX() - origin.getX()), Math.abs(pos.getZ() - origin.getZ()));
	}

	/** 四边正中的过道，不挖。 */
	public static boolean isBridge(BlockPos chest, BlockPos pos) {
		BlockPos ground = ground(chest);
		if (pos.getY() != ground.getY() || chebyshev(ground, pos) != DITCH_RADIUS) {
			return false;
		}
		int dx = pos.getX() - ground.getX();
		int dz = pos.getZ() - ground.getZ();
		return dx == 0 || dz == 0;
	}

	public static boolean isDitch(BlockPos chest, BlockPos pos) {
		BlockPos ground = ground(chest);
		return pos.getY() == ground.getY()
			&& chebyshev(ground, pos) == DITCH_RADIUS
			&& !isBridge(chest, pos);
	}

	/** 箱子正下方那一格留着，箱子就放在上面。 */
	public static boolean isPlot(BlockPos chest, BlockPos pos) {
		BlockPos ground = ground(chest);
		return pos.getY() == ground.getY()
			&& !pos.equals(ground)
			&& chebyshev(ground, pos) <= PLOT_RADIUS;
	}

	/**
	 * 西瓜、南瓜的茎只种在离中心的 x 距离为奇数的格子上。
	 * 偶数列留空，果实长到旁边去。
	 */
	public static boolean gourdStem(BlockPos chest, BlockPos plot) {
		int dx = Math.abs(plot.getX() - ground(chest).getX());
		return dx % 2 == 1;
	}

	/** 从东北角起顺时针绕一圈，跳过四边正中。 */
	public static List<BlockPos> ditchRing(BlockPos chest) {
		BlockPos ground = ground(chest);
		int x = ground.getX();
		int y = ground.getY();
		int z = ground.getZ();
		int radius = DITCH_RADIUS;
		List<BlockPos> ring = new ArrayList<>();
		for (int dx = radius; dx >= -radius; dx--) {
			addDitch(ring, chest, x + dx, y, z + radius);
		}
		for (int dz = radius - 1; dz >= -radius; dz--) {
			addDitch(ring, chest, x - radius, y, z + dz);
		}
		for (int dx = -radius + 1; dx <= radius; dx++) {
			addDitch(ring, chest, x + dx, y, z - radius);
		}
		for (int dz = -radius + 1; dz <= radius - 1; dz++) {
			addDitch(ring, chest, x + radius, y, z + dz);
		}
		return ring;
	}

	public static void forEachDitch(BlockPos chest, Consumer<BlockPos> consumer) {
		for (BlockPos pos : ditchRing(chest)) {
			consumer.accept(pos);
		}
	}

	public static void forEachPlot(BlockPos chest, Consumer<BlockPos> consumer) {
		BlockPos ground = ground(chest);
		for (int dx = -PLOT_RADIUS; dx <= PLOT_RADIUS; dx++) {
			for (int dz = -PLOT_RADIUS; dz <= PLOT_RADIUS; dz++) {
				BlockPos pos = ground.offset(dx, 0, dz);
				if (isPlot(chest, pos)) {
					consumer.accept(pos);
				}
			}
		}
	}

	/** 四个角，水从这里流到过道跟前。 */
	public static void forEachWaterCorner(BlockPos chest, Consumer<BlockPos> consumer) {
		BlockPos ground = ground(chest);
		int radius = DITCH_RADIUS;
		consumer.accept(ground.offset(radius, 0, radius));
		consumer.accept(ground.offset(-radius, 0, radius));
		consumer.accept(ground.offset(-radius, 0, -radius));
		consumer.accept(ground.offset(radius, 0, -radius));
	}

	private static void addDitch(List<BlockPos> ring, BlockPos chest, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (isDitch(chest, pos)) {
			ring.add(pos);
		}
	}
}
