package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Хрустальная жеода — маленькая, не больше трёх блоков в радиусе.
 *
 * <p>Слои снаружи внутрь: гладкий базальт, кальцит, аметист и крошечная полость
 * в середине, где на стенках сидят аметистовые друзы. В аметистовой выстилке
 * — один-два блока Монолитного хрусталя, изредка больше, но никогда не больше
 * пяти: жеода — находка, а не рудник.
 *
 * <p>Ставится только в сплошную породу и замещает только её: пещера, прошедшая
 * рядом, вскрывает жеоду, и тогда её видно из хода.
 */
public class CrystalGeodeFeature extends Feature<NoneFeatureConfiguration> {

    private static final double HOLLOW = 1.3;
    private static final double AMETHYST = 2.0;
    private static final double CALCITE = 2.55;
    private static final double SHELL = 3.0;

    /** Меньше этой доли породы в шаре — жеода висела бы в пустоте, не ставим. */
    private static final double SOLID_SHARE = 0.6;

    private static final int MAX_CRYSTALS = 5;

    public CrystalGeodeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos centre = context.origin();
        int reach = (int) Math.ceil(SHELL);

        int solid = 0;
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
            if (pos.distSqr(centre) <= SHELL * SHELL) {
                total++;
                if (Terrain.rock(level.getBlockState(pos))) {
                    solid++;
                }
            }
        }
        if (solid < total * SOLID_SHARE) {
            return false;
        }

        List<BlockPos> lining = new ArrayList<>();
        List<BlockPos> hollow = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
            // Край только внутрь: граница гуляет, но шар не выходит за радиус.
            double distance = Math.sqrt(pos.distSqr(centre)) + random.nextDouble() * 0.3;
            if (distance > SHELL) {
                continue;
            }
            BlockPos cell = pos.immutable();
            BlockState state = level.getBlockState(cell);
            if (distance <= HOLLOW && state.isAir()) {
                hollow.add(cell);
                continue;
            }
            if (!Terrain.rock(state)) {
                continue;
            }
            if (distance <= HOLLOW) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                hollow.add(cell);
            } else if (distance <= AMETHYST) {
                level.setBlock(cell, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                lining.add(cell);
            } else if (distance <= CALCITE) {
                level.setBlock(cell, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(cell, Blocks.SMOOTH_BASALT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        crystals(level, random, lining);
        druses(level, random, hollow);
        return true;
    }

    /**
     * Монолитный хрусталь в выстилке — один, а дальше каждый следующий всё
     * реже: второй с шансом 40 %, третий 16 %, и так до пяти.
     */
    private static void crystals(WorldGenLevel level, RandomSource random, List<BlockPos> lining) {
        int count = 1;
        while (count < MAX_CRYSTALS && random.nextFloat() < 0.4F) {
            count++;
        }
        BlockState crystal = GimpanumContent.MONOLITHIC_CRYSTAL.get().defaultBlockState();
        for (int i = 0; i < count && !lining.isEmpty(); i++) {
            level.setBlock(lining.remove(random.nextInt(lining.size())), crystal, Block.UPDATE_CLIENTS);
        }
    }

    /** Друзы на стенках полости — каждая растёт от аметиста, к которому прилипла. */
    private static void druses(WorldGenLevel level, RandomSource random, List<BlockPos> hollow) {
        Block[] sizes = {Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
                Blocks.AMETHYST_CLUSTER};
        for (BlockPos pos : hollow) {
            if (random.nextFloat() > 0.5F) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (level.getBlockState(pos.relative(side)).is(Blocks.AMETHYST_BLOCK)) {
                    level.setBlock(pos, sizes[random.nextInt(sizes.length)].defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, side.getOpposite()), Block.UPDATE_CLIENTS);
                    break;
                }
            }
        }
    }
}
