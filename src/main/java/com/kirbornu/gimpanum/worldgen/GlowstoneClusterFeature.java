package com.kirbornu.gimpanum.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Гроздья светокамня, свисающие с потолка лабиринта.
 *
 * <p>Растут как в Незере: от точки на своде вниз, и каждый новый блок ставится
 * только туда, где он касается ровно одного уже поставленного. Отсюда
 * ветвистые сосульки, а не сплошной ком.
 *
 * <p>Света в Гимпануме и так в избытке — измерение освещено целиком, — так что
 * гроздья здесь не лампы, а залежь, которую видно издалека.
 */
public class GlowstoneClusterFeature extends Feature<NoneFeatureConfiguration> {

    /** Насколько высоко над точкой размещения ищем свод. */
    private static final int CEILING_SEARCH = 16;

    /** Сколько раз пробовать прирастить блок — от этого размер грозди. */
    private static final int GROWTH_TRIES = 500;

    public GlowstoneClusterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos pos = context.origin();
        if (!level.isEmptyBlock(pos)) {
            return false;
        }
        int climbed = 0;
        while (level.isEmptyBlock(pos.above()) && climbed++ < CEILING_SEARCH) {
            pos = pos.above();
        }
        if (!Terrain.rock(level.getBlockState(pos.above()))) {
            return false;
        }

        BlockState glowstone = Blocks.GLOWSTONE.defaultBlockState();
        level.setBlock(pos, glowstone, Block.UPDATE_CLIENTS);
        for (int i = 0; i < GROWTH_TRIES; i++) {
            BlockPos next = pos.offset(random.nextInt(8) - random.nextInt(8), -random.nextInt(12),
                    random.nextInt(8) - random.nextInt(8));
            if (level.isEmptyBlock(next) && touching(level, next) == 1) {
                level.setBlock(next, glowstone, Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    private static int touching(WorldGenLevel level, BlockPos pos) {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).is(Blocks.GLOWSTONE)) {
                count++;
            }
        }
        return count;
    }
}
