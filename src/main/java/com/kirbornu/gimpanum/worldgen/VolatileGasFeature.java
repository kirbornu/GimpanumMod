package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Карман летучего газа — несколько блоков, сросшихся комком.
 *
 * <p>Растёт от точки случайными шагами в стороны, как капля, а не шаром: ровный
 * шарик из пяти блоков читался бы как постройка. Замещает только породу —
 * часть карманов выходит на стены пещер и видна, часть спрятана в толще и
 * находится лишь киркой.
 */
public class VolatileGasFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_SIZE = 2;
    private static final int MAX_SIZE = 8;

    public VolatileGasFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockState gas = GimpanumContent.VOLATILE_NEBULA_GAS.get().defaultBlockState();

        int size = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
        BlockPos cursor = context.origin();
        int placed = 0;
        for (int step = 0; step < size * 3 && placed < size; step++) {
            if (Terrain.rock(level.getBlockState(cursor))) {
                setBlock(level, cursor, gas);
                placed++;
            }
            BlockPos next = cursor.relative(Direction.getRandom(random));
            // Не уходить от точки отсчёта дальше пары блоков: карман, а не нить.
            if (next.distManhattan(context.origin()) <= 3) {
                cursor = next;
            }
        }
        return placed > 0;
    }
}
