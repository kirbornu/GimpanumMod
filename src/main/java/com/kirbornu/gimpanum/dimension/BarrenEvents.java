package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * В Гимпануме ничего не растёт.
 *
 * <p>Без воздуха и без света это единственное последовательное поведение:
 * вывезти грядку в измерение, где нет ни погоды, ни смены суток, и получать
 * урожай под защитой клейма было бы слишком удобно. Растения ставятся и
 * стоят, но не переходят в следующую стадию.
 *
 * <p>Три события, потому что игра растит по-разному: обычные посевы стадиями,
 * саженцы — превращением в структуру дерева, и всё это отдельно от костной
 * муки.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class BarrenEvents {

    private BarrenEvents() {
    }

    /** Посевы, кактусы, тростник, хорус — всё, что зреет стадиями. */
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (inGimpanum(event.getLevel())) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    /** Саженцы, грибы, азалия — то, что вырастает сразу структурой. */
    @SubscribeEvent
    public static void onGrowFeature(BlockGrowFeatureEvent event) {
        if (inGimpanum(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    /** Костная мука тратится, но ничего не даёт. */
    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (inGimpanum(event.getLevel())) {
            event.setSuccessful(false);
        }
    }

    private static boolean inGimpanum(LevelAccessor accessor) {
        return accessor instanceof Level level && NebulaPortal.GIMPANUM.equals(level.dimension());
    }
}
