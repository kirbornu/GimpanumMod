package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Небула-плод — нарост на стволе пещерной поросли.
 *
 * <p>Внешне это какао-боб, перекрашенный в бело-голубое, и держится он так же:
 * висит на боку бревна, глядя на него свойством {@code facing}. Отличий от
 * какао два, и оба принципиальные. Первое — стадий нет: в Гимпануме ничего не
 * растёт, поэтому плод сразу и навсегда спелый. Второе — внутри него не боб, а
 * Осколок хрусталя, и задано это таблицей добычи, а не кодом.
 *
 * <p>Предмета у плода нет намеренно: его не ставят, его снимают.
 */
public class NebulaFruitBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<NebulaFruitBlock> CODEC = simpleCodec(NebulaFruitBlock::new);

    /**
     * Коробка плода — та же, что у спелого какао, по одной на сторону.
     *
     * <p>Считаем от направления, а не выписываем четыре набора чисел: плод
     * симметричен, и вся разница между сторонами — к какой грани он прижат.
     */
    private static final VoxelShape[] SHAPES = new VoxelShape[Direction.values().length];

    static {
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(4.0, 3.0, 7.0, 12.0, 12.0, 15.0);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(4.0, 3.0, 1.0, 12.0, 12.0, 9.0);
        SHAPES[Direction.EAST.ordinal()] = Block.box(7.0, 3.0, 4.0, 15.0, 12.0, 12.0);
        SHAPES[Direction.WEST.ordinal()] = Block.box(1.0, 3.0, 4.0, 9.0, 12.0, 12.0);
    }

    public NebulaFruitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    /** Держится только на небула-бревне — и на обычном, и на ободранном. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState host = level.getBlockState(pos.relative(state.getValue(FACING)));
        return host.is(GimpanumContent.NEBULA_LOG.get())
                || host.is(GimpanumContent.STRIPPED_NEBULA_LOG.get());
    }

    /** Ствол убрали — плоду не на чем висеть. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == state.getValue(FACING) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : state;
    }
}
