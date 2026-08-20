package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Озеро раскалённого небула-газа в толще породы.
 *
 * <p>Своя фича, а не ванильная руда: рудная жила — это несколько десятков
 * блоков вдоль отрезка, и как её ни раздувай, озером она не станет. Здесь же
 * нужен именно объём — полость в тысячи блоков, в которую можно провалиться.
 *
 * <p>Форма — эллипсоид со рваным краем: радиус по каждой оси свой, а граница
 * гуляет от точки к точке. Ровный шар в породе читался бы как постройка.
 *
 * <p>Заполняем только породу. Пещёры и уже готовые полости не трогаем — газ
 * заперт в камне, и найти его можно, лишь прокопавшись.
 */
public class NebulaGasFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * Наименьший и наибольший радиус по горизонтали.
     *
     * <p>Потолок здесь не на глаз: при генерации фича вправе писать лишь в
     * свой чанк и восемь соседних. Точка отсчёта стоит где угодно внутри
     * своего чанка, поэтому любой радиус до шестнадцати гарантированно
     * остаётся в пределах соседа, а больший — уже нет. С семнадцатью игра
     * ругалась «setBlock in a far chunk» и молча теряла край озера.
     */
    private static final int MIN_RADIUS = 9;
    private static final int MAX_RADIUS = 14;

    /** Насколько озеро приплюснуто: по высоте оно всегда меньше, чем вширь. */
    private static final double FLATTEN = 0.55;

    /** Насколько гуляет край, долей радиуса. */
    private static final double RAGGED = 0.22;

    public NebulaGasFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos centre = context.origin();

        int spanX = Mth.nextInt(random, MIN_RADIUS, MAX_RADIUS);
        int spanZ = Mth.nextInt(random, MIN_RADIUS, MAX_RADIUS);
        int spanY = Math.max(3, (int) ((spanX + spanZ) * 0.5 * FLATTEN));

        BlockState gas = GimpanumContent.SCORCHING_NEBULA_GAS.get().defaultBlockState();
        boolean placed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -spanX; dx <= spanX; dx++) {
            for (int dy = -spanY; dy <= spanY; dy++) {
                for (int dz = -spanZ; dz <= spanZ; dz++) {
                    double reach = sq(dx, spanX) + sq(dy, spanY) + sq(dz, spanZ);
                    if (reach > 1.0 - RAGGED + random.nextDouble() * RAGGED * 2.0) {
                        continue;
                    }
                    cursor.setWithOffset(centre, dx, dy, dz);
                    if (!fillable(level, cursor)) {
                        continue;
                    }
                    setBlock(level, cursor, gas);
                    placed = true;
                }
            }
        }
        return placed;
    }

    private static double sq(int offset, int span) {
        double ratio = (double) offset / span;
        return ratio * ratio;
    }

    /** Порода — да, пустота и чужое — нет. */
    private static boolean fillable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(GimpanumContent.COSMIC_SAND.get()) || state.is(GimpanumContent.COSMIC_ASH.get());
    }
}
