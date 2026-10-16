package com.tradinggolem.block;

import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.entity.TradingGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TradingChestBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

	public TradingChestBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide()
				? createTickerHelper(type, ModBlocks.TRADING_CHEST_ENTITY, TradingChestBlockEntity::lidAnimateTick)
				: null;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof TradingChestBlockEntity chest) {
			chest.recheckOpen();
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(this));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TradingChestBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof TradingChestBlockEntity chest) {
			player.openMenu(chest);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (oldState.is(this) || !(level instanceof ServerLevel server)) {
			return;
		}
		spawnGolem(server, pos, state.getValue(FACING));
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		dismissGolem(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	public static void spawnGolem(ServerLevel level, BlockPos chestPos, Direction facing) {
		if (golemAt(level, chestPos) != null) {
			return;
		}
		TradingGolem golem = ModEntityTypes.TRADING_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return;
		}
		BlockPos stand = findStand(level, chestPos, facing);
		golem.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, facing.toYRot(), 0.0F);
		golem.setYHeadRot(facing.toYRot());
		golem.setPersistenceRequired();
		golem.bindChest(chestPos);
		level.addFreshEntity(golem);
		level.playSound(null, stand, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.0F, 1.15F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, stand.getX() + 0.5, stand.getY() + 0.6, stand.getZ() + 0.5, 16, 0.35, 0.4, 0.35, 0.02);
	}

	public static void dismissGolem(ServerLevel level, BlockPos chestPos) {
		TradingGolem golem = golemAt(level, chestPos);
		if (golem != null) {
			golem.discard();
		}
	}

	@Nullable
	private static TradingGolem golemAt(ServerLevel level, BlockPos chestPos) {
		return level.getEntitiesOfClass(TradingGolem.class, new net.minecraft.world.phys.AABB(chestPos).inflate(96.0), entity -> chestPos.equals(entity.chestPos()))
				.stream()
				.findFirst()
				.orElse(null);
	}

	private static BlockPos findStand(ServerLevel level, BlockPos chestPos, Direction facing) {
		BlockPos front = chestPos.relative(facing);
		if (canStand(level, front)) {
			return front;
		}
		BlockPos above = chestPos.above();
		if (canStand(level, above)) {
			return above;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos beside = chestPos.relative(direction);
			if (canStand(level, beside)) {
				return beside;
			}
		}
		return front;
	}

	private static boolean canStand(Level level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}
}
