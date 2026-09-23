package com.kirbornu.gimpanum.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Кварцевая трещина — от бедрока до поверхности, а над ней травинками.
 *
 * <p>Внизу это прожилка из кварцевых блоков, которая поднимается сквозь всю
 * толщу, петляя, изредка утолщаясь и выпуская короткие отростки вбок. Она
 * режет стены лабиринта, и по ней видно, что тянется она откуда-то снизу и
 * куда-то вверх.
 *
 * <p>Над барханами трещина не кончается: из неё вырастают одна-три тонкие
 * колонны из кварцевых столбов, каждая клонится в свою сторону, и тем
 * сильнее, чем выше, — как огромные травинки. По ним трещину и находят с
 * поверхности.
 *
 * <p>Размах ограничен тем, что фича вправе писать лишь в свой чанк и восемь
 * соседних: петли и наклон вместе не уводят дальше пятнадцати блоков от
 * точки отсчёта.
 */
public class QuartzCrackFeature extends Feature<NoneFeatureConfiguration> {

    /** Насколько далеко трещина петляет от точки отсчёта. */
    private static final int WANDER = 9;

    /** Насколько далеко отходят отростки. */
    private static final int BRANCH_REACH = 13;

    /** Наибольший наклон травинки у верхушки. */
    private static final double MAX_LEAN = 6.0;

    public QuartzCrackFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();

        int x = origin.getX();
        int z = origin.getZ();
        int y = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (y < level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)) {
            intoRock(level, cursor.set(x, y, z), quartz);
            if (random.nextFloat() < 0.35F) {
                intoRock(level, cursor.set(x, y, z).move(Direction.Plane.HORIZONTAL.getRandomDirection(random)), quartz);
            }
            if (random.nextFloat() < 0.04F) {
                branch(level, random, origin, x, y, z, quartz);
            }
            if (random.nextFloat() < 0.25F) {
                if (random.nextBoolean()) {
                    x = Mth.clamp(x + (random.nextBoolean() ? 1 : -1), origin.getX() - WANDER, origin.getX() + WANDER);
                } else {
                    z = Mth.clamp(z + (random.nextBoolean() ? 1 : -1), origin.getZ() - WANDER, origin.getZ() + WANDER);
                }
            }
            y++;
        }

        int blades = 1 + random.nextInt(3);
        for (int i = 0; i < blades; i++) {
            blade(level, random, x, y, z);
        }
        return true;
    }

    /** Отросток вбок и чуть вверх — несколько блоков по диагонали. */
    private static void branch(WorldGenLevel level, RandomSource random, BlockPos origin,
                               int x, int y, int z, BlockState quartz) {
        int dx = random.nextInt(3) - 1;
        int dz = dx == 0 ? (random.nextBoolean() ? 1 : -1) : random.nextInt(3) - 1;
        int length = 4 + random.nextInt(7);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step < length; step++) {
            x += dx;
            z += dz;
            y += random.nextInt(2);
            if (Math.abs(x - origin.getX()) > BRANCH_REACH || Math.abs(z - origin.getZ()) > BRANCH_REACH) {
                return;
            }
            intoRock(level, cursor.set(x, y, z), quartz);
        }
    }

    /**
     * Травинка: столб, клонящийся тем сильнее, чем выше.
     *
     * <p>Смещение растёт как квадрат высоты — у основания стоит почти прямо, у
     * верхушки заваливается. Когда смещение прыгает на клетку, ставим
     * перемычку на той же высоте, чтобы травинка не рассыпалась на висящие по
     * диагонали блоки; у перемычки столб лежит поперёк.
     */
    private static void blade(WorldGenLevel level, RandomSource random, int baseX, int baseY, int baseZ) {
        int height = 6 + random.nextInt(9);
        double lean = 2.0 + random.nextDouble() * (MAX_LEAN - 2.0);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int top = Math.min(baseY + height, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = baseX;
        int z = baseZ;
        for (int y = baseY; y < top; y++) {
            double t = (double) (y - baseY) / Math.max(1, height - 1);
            double offset = lean * t * t;
            int wantX = baseX + (int) Math.round(Math.cos(angle) * offset);
            int wantZ = baseZ + (int) Math.round(Math.sin(angle) * offset);
            while (x != wantX) {
                x += Integer.signum(wantX - x);
                intoAir(level, cursor.set(x, y, z), Direction.Axis.X);
            }
            while (z != wantZ) {
                z += Integer.signum(wantZ - z);
                intoAir(level, cursor.set(x, y, z), Direction.Axis.Z);
            }
            intoAir(level, cursor.set(x, y, z), Direction.Axis.Y);
        }
    }

    private static void intoRock(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (Terrain.rock(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void intoAir(WorldGenLevel level, BlockPos pos, Direction.Axis axis) {
        if (level.isEmptyBlock(pos)) {
            level.setBlock(pos, Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis),
                    Block.UPDATE_CLIENTS);
        }
    }
}
