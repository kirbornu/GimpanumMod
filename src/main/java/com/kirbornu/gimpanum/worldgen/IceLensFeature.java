package com.kirbornu.gimpanum.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Ледяная линза на барханах — пятно синего льда вместо песка.
 *
 * <p>Вытянутый овал, повёрнутый как придётся, с рваным краем; в середине лёд
 * лежит в два слоя, по краям в один. Лежит заподлицо с песком и повторяет
 * рельеф: каждая колонка берёт свою поверхность. На нём скользят все — и
 * игрок, и отряд ходоков, который за ним гонится.
 */
public class IceLensFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 8;

    /** Ближе этой доли к середине лёд лежит в два слоя. */
    private static final double THICK_CORE = 0.5;

    public IceLensFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos centre = context.origin();

        double length = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        double width = MIN_RADIUS + random.nextDouble() * (length - MIN_RADIUS);
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int reach = (int) Math.ceil(length);

        BlockState ice = Blocks.BLUE_ICE.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double along = (dx * cos + dz * sin) / length;
                double across = (-dx * sin + dz * cos) / width;
                double reachOf = along * along + across * across;
                if (reachOf > 1.0 - random.nextDouble() * 0.25) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int layers = reachOf < THICK_CORE * THICK_CORE ? 2 : 1;
                for (int y = top; y > top - layers; y--) {
                    if (Terrain.rock(level.getBlockState(cursor.set(x, y, z)))) {
                        level.setBlock(cursor, ice, Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }
}
