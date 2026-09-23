package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
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
 * Метеоритная воронка в барханах.
 *
 * <p>Чаша с опалённым пеплом дном, вал по краю, брызги пепла вокруг — и на
 * дне полузарытое ядро: сырое железо вперемешку с магмой, чернокаменной
 * коркой и железной рудой. Магма жжёт так же, как раскалённый газ, так что
 * к ядру спускаются, присев.
 *
 * <p>Каждая колонка меряется от своей поверхности, а не от центра: барханы
 * неровные, и чаша, вырезанная от одной высоты, на склоне висела бы в
 * воздухе или уходила в песок. Вынимается и насыпается только природная
 * порода — портал или астероид, оказавшиеся рядом, воронка не режет.
 *
 * <p>Вал и брызги не дальше пятнадцати блоков от центра: дальше фиче писать
 * нельзя.
 */
public class MeteorCraterFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 5;
    private static final int MAX_RADIUS = 9;

    /** Глубина чаши — доля радиуса. */
    private static final double DEPTH = 0.5;

    /** Вал — от края чаши до этой доли радиуса наружу. */
    private static final double RIM = 1.4;

    /** Высота вала — доля радиуса. */
    private static final double RIM_HEIGHT = 0.3;

    /** Брызги пепла — до этой доли радиуса. */
    private static final double EJECTA = 1.7;

    public MeteorCraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos centre = context.origin();
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int reach = (int) Math.ceil(radius * EJECTA);

        BlockState sand = GimpanumContent.COSMIC_SAND.get().defaultBlockState();
        BlockState ash = GimpanumContent.COSMIC_ASH.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int floorY = centre.getY();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6 - 0.3;
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);

                if (r < radius) {
                    // Чаша: глубже к середине, по параболе.
                    int depth = (int) Math.round(radius * DEPTH * (1.0 - (r / radius) * (r / radius)));
                    for (int y = ground - 1; y >= ground - depth; y--) {
                        carve(level, cursor.set(x, y, z));
                    }
                    BlockPos bottom = cursor.set(x, ground - depth - 1, z);
                    if (Terrain.rock(level.getBlockState(bottom)) && random.nextFloat() < 0.7F) {
                        level.setBlock(bottom, ash, Block.UPDATE_CLIENTS);
                    }
                    if (dx == 0 && dz == 0) {
                        floorY = ground - depth;
                    }
                } else if (r < radius * RIM) {
                    // Вал: выше всего у самого края, к наружи сходит на нет.
                    int height = (int) Math.round(radius * RIM_HEIGHT * (1.0 - (r - radius) / (radius * (RIM - 1.0))));
                    for (int y = ground; y < ground + height; y++) {
                        fill(level, cursor.set(x, y, z), random.nextFloat() < 0.3F ? ash : sand);
                    }
                } else if (r < radius * EJECTA && random.nextFloat() < 0.25F) {
                    // Брызги: пепел поверх песка, тем реже, чем дальше.
                    BlockPos top = cursor.set(x, ground - 1, z);
                    if (Terrain.rock(level.getBlockState(top))) {
                        level.setBlock(top, ash, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        core(level, random, new BlockPos(centre.getX(), floorY, centre.getZ()));
        return true;
    }

    /** Ядро — шар, наполовину ушедший в дно чаши. */
    private static void core(WorldGenLevel level, RandomSource random, BlockPos centre) {
        double size = 1.6 + random.nextDouble() * 0.8;
        int reach = (int) Math.ceil(size);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
            if (pos.distSqr(centre) > size * size) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !Terrain.rock(state)) {
                continue;
            }
            level.setBlock(pos, piece(random), Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState piece(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.45F) {
            return Blocks.RAW_IRON_BLOCK.defaultBlockState();
        }
        if (roll < 0.65F) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (roll < 0.80F) {
            return GimpanumContent.COSMIC_ASH.get().defaultBlockState();
        }
        if (roll < 0.90F) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return Blocks.IRON_ORE.defaultBlockState();
    }

    private static void carve(WorldGenLevel level, BlockPos pos) {
        if (Terrain.rock(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void fill(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.isEmptyBlock(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
