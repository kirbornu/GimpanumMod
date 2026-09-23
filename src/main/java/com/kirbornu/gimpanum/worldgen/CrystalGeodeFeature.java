package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
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
 * <p>Своя, а не ванильная аметистовая: слои снаружи внутрь — Космический пепел,
 * Хрустальная корка и крошечная полость в середине. В корке — один-два блока
 * Монолитного хрусталя, изредка больше, но никогда не больше пяти: жеода —
 * находка, а не рудник.
 *
 * <p>Ставится только в сплошную породу и замещает только её: пещера, прошедшая
 * рядом, вскрывает жеоду, и тогда её видно из хода.
 */
public class CrystalGeodeFeature extends Feature<NoneFeatureConfiguration> {

    private static final double HOLLOW = 1.3;
    private static final double CRUST = 2.0;
    private static final double SHELL = 2.8;

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
        BlockState crust = GimpanumContent.CRYSTAL_CRUST.get().defaultBlockState();
        BlockState ash = GimpanumContent.COSMIC_ASH.get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
            // Край только внутрь: граница гуляет, но шар не выходит за радиус.
            double distance = Math.sqrt(pos.distSqr(centre)) + random.nextDouble() * 0.3;
            if (distance > SHELL) {
                continue;
            }
            BlockPos cell = pos.immutable();
            if (!Terrain.rock(level.getBlockState(cell))) {
                continue;
            }
            if (distance <= HOLLOW) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (distance <= CRUST) {
                level.setBlock(cell, crust, Block.UPDATE_CLIENTS);
                lining.add(cell);
            } else {
                level.setBlock(cell, ash, Block.UPDATE_CLIENTS);
            }
        }

        crystals(level, random, lining);
        return true;
    }

    /**
     * Монолитный хрусталь в корке — один, а дальше каждый следующий всё
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
}
