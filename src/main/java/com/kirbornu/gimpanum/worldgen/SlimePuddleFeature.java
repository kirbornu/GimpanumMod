package com.kirbornu.gimpanum.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Лужа слизи на полу лабиринта.
 *
 * <p>Пятно в один слой, заподлицо с полом: издалека его легко не заметить,
 * а при пониженной тяжести Гимпанума слизь подбрасывает упавшего куда выше,
 * чем дома, — под самый свод. Посреди погони это и спасение, и ловушка.
 *
 * <p>Пол у лабиринта неровный, поэтому каждая клетка лужи ищет свой пол в
 * паре блоков вверх и вниз, а не ставится на одной высоте.
 */
public class SlimePuddleFeature extends Feature<NoneFeatureConfiguration> {

    private static final int FLOOR_SEARCH = 16;
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;

    /** Насколько пол под лужей может гулять по высоте. */
    private static final int STEP = 2;

    public SlimePuddleFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int descended = 0;
        while (level.isEmptyBlock(pos.below()) && descended++ < FLOOR_SEARCH) {
            pos = pos.below();
        }
        if (!Terrain.rock(level.getBlockState(pos.below()))) {
            return false;
        }

        double radius = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        int reach = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > radius - random.nextDouble() * 0.8) {
                    continue;
                }
                for (int dy = STEP; dy >= -STEP; dy--) {
                    cursor.set(pos.getX() + dx, pos.getY() - 1 + dy, pos.getZ() + dz);
                    if (Terrain.rock(level.getBlockState(cursor)) && level.isEmptyBlock(cursor.above())) {
                        level.setBlock(cursor, Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                        placed = true;
                        break;
                    }
                }
            }
        }
        return placed;
    }
}
