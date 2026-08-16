package com.kirbornu.gimpanum.worldgen;

import com.mojang.serialization.Codec;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Древовидная поросль пещерных лабиринтов Гимпанума.
 *
 * <p>Своя фича, а не ванильное дерево: ванильное требует под собой землю и
 * растёт вверх от поверхности, а тут нужно нашарить пол пещеры где-то в толще
 * песка и вырастить от него кривую ветвистую штуку, которая упирается в свод.
 * Листвы нет намеренно — это не растение, а то, что на растение похоже.
 *
 * <p>Ствол идёт вверх, изредка отступая вбок, ветви расходятся из верхней
 * половины, у основания расползаются короткие корни. Ось каждого бревна
 * ставится по направлению шага, поэтому изгибы читаются как изгибы, а не как
 * лесенка из вертикальных чурбаков.
 */
public class NebulaTreeFeature extends Feature<NoneFeatureConfiguration> {

    /** Насколько глубоко под точкой размещения ищем пол пещеры. */
    private static final int FLOOR_SEARCH = 20;

    /** Меньше этого просвета над полом дерево не ставим — получится обрубок. */
    private static final int MIN_HEADROOM = 5;

    public NebulaTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();

        BlockPos base = findFloor(level, context.origin());
        if (base == null || headroom(level, base) < MIN_HEADROOM) {
            return false;
        }

        int height = 4 + random.nextInt(5);
        BlockPos top = trunk(level, random, base, height);
        branches(level, random, base, top);
        roots(level, random, base);
        return true;
    }

    /** Первая свободная клетка над твердью, если она есть под точкой размещения. */
    private static BlockPos findFloor(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i < FLOOR_SEARCH; i++) {
            if (level.isEmptyBlock(cursor) && !level.isEmptyBlock(cursor.below())) {
                return cursor.immutable();
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static int headroom(WorldGenLevel level, BlockPos base) {
        int free = 0;
        BlockPos.MutableBlockPos cursor = base.mutable();
        while (free < 32 && level.isEmptyBlock(cursor)) {
            free++;
            cursor.move(Direction.UP);
        }
        return free;
    }

    /** Ствол: вверх, изредка со сдвигом вбок. Возвращает верхушку. */
    private BlockPos trunk(WorldGenLevel level, RandomSource random, BlockPos base, int height) {
        BlockPos cursor = base;
        for (int i = 0; i < height; i++) {
            boolean sideways = i > 0 && i < height - 1 && random.nextInt(4) == 0;
            Direction step = sideways ? Direction.Plane.HORIZONTAL.getRandomDirection(random) : Direction.UP;
            if (!log(level, cursor, step.getAxis())) {
                return cursor;
            }
            BlockPos next = cursor.relative(step);
            if (!level.isEmptyBlock(next)) {
                return cursor;
            }
            cursor = next;
        }
        log(level, cursor, Direction.Axis.Y);
        return cursor;
    }

    /** Ветви из верхней половины ствола: шаг вбок, шаг вверх, и так до упора. */
    private void branches(WorldGenLevel level, RandomSource random, BlockPos base, BlockPos top) {
        int count = 2 + random.nextInt(3);
        int span = Math.max(1, top.getY() - base.getY() - 1);
        for (int i = 0; i < count; i++) {
            int y = base.getY() + span / 2 + random.nextInt(Math.max(1, span / 2 + 1));
            BlockPos cursor = new BlockPos(top.getX(), Math.min(y, top.getY()), top.getZ());
            Direction out = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int length = 2 + random.nextInt(4);
            for (int step = 0; step < length; step++) {
                Direction move = step % 2 == 0 || random.nextInt(3) == 0 ? out : Direction.UP;
                BlockPos next = cursor.relative(move);
                if (!level.isEmptyBlock(next)) {
                    break;
                }
                cursor = next;
                log(level, cursor, move.getAxis());
            }
        }
    }

    /** Короткие корни: вбок от основания и на клетку вниз, если там пусто. */
    private void roots(WorldGenLevel level, RandomSource random, BlockPos base) {
        for (Direction out : Direction.Plane.HORIZONTAL) {
            if (random.nextInt(3) == 0) {
                continue;
            }
            BlockPos side = base.relative(out);
            if (!level.isEmptyBlock(side)) {
                continue;
            }
            log(level, side, out.getAxis());
            BlockPos down = side.below();
            if (random.nextBoolean() && level.isEmptyBlock(down)) {
                log(level, down, Direction.Axis.Y);
            }
        }
    }

    private boolean log(WorldGenLevel level, BlockPos pos, Direction.Axis axis) {
        if (!level.isEmptyBlock(pos)) {
            return false;
        }
        BlockState state = GimpanumContent.NEBULA_LOG.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, axis);
        setBlock(level, pos, state);
        return true;
    }
}
