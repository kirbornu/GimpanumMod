package com.kirbornu.gimpanum.sublevel;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Единственная точка входа к поддержке физических конструкций.
 *
 * <p>Sable — необязательная зависимость, поэтому здесь нет ни одного его типа.
 * Все обращения уходят в {@link SableBridge}, который загружается JVM только в
 * момент первого вызова — то есть лишь после того, как проверка
 * {@link #isSableLoaded()} прошла успешно. Если Sable нет, класс-мост не
 * тронется загрузчиком и NoClassDefFoundError не возникнет.
 */
public final class SubLevelSupport {

    private static final String SABLE_MOD_ID = "sable";

    private static Boolean sableLoaded;
    private static boolean bridgeFailed;

    private SubLevelSupport() {
    }

    public static boolean isSableLoaded() {
        if (sableLoaded == null) {
            sableLoaded = ModList.get().isLoaded(SABLE_MOD_ID);
            Gimpanum.LOGGER.info("Sable {} — поддержка конструкций {}",
                    sableLoaded ? "обнаружен" : "не найден",
                    sableLoaded ? "включена" : "отключена");
        }
        return sableLoaded;
    }

    /**
     * Описывает конструкцию, на которой стоит блок.
     *
     * @return пустое значение, если Sable не установлен либо блок стоит в
     *         обычном мире, а не на конструкции
     */
    public static Optional<SubLevelInfo> describe(Level level, BlockPos pos) {
        if (!isSableLoaded() || bridgeFailed) {
            return Optional.empty();
        }
        try {
            return SableBridge.describe(level, pos);
        } catch (Throwable t) {
            // Ломать игру из-за несовместимости с чужим модом нельзя: гасим
            // мост целиком и дальше работаем как будто Sable нет.
            bridgeFailed = true;
            Gimpanum.LOGGER.error("Мост к Sable отключён после ошибки; "
                    + "поддержка конструкций выключена до перезапуска", t);
            return Optional.empty();
        }
    }

    /** {@code true}, если блок стоит на физической конструкции. */
    public static boolean isOnSubLevel(Level level, BlockPos pos) {
        return describe(level, pos).isPresent();
    }

    /**
     * Мировая позиция центра блока — та точка, где игроки его действительно
     * видят. Для блока в обычном мире это просто центр блока.
     *
     * <p>Все эффекты (взрыв, дроп, позиция для команд) обязаны считаться
     * отсюда, а не от {@code BlockPos}: у блока на конструкции {@code BlockPos}
     * указывает в служебный регион карты.
     */
    public static Vec3 worldCenter(Level level, BlockPos pos) {
        if (!isSableLoaded() || bridgeFailed) {
            return Vec3.atCenterOf(pos);
        }
        try {
            // Облегчённый путь: это вызывается по нескольку раз в секунду на
            // каждое Ядро, и собирать полное описание ради одной точки незачем.
            Vec3 worldCenter = SableBridge.worldCenterOrNull(level, pos);
            return worldCenter != null ? worldCenter : Vec3.atCenterOf(pos);
        } catch (Throwable t) {
            bridgeFailed = true;
            Gimpanum.LOGGER.error("Мост к Sable отключён после ошибки; "
                    + "поддержка конструкций выключена до перезапуска", t);
            return Vec3.atCenterOf(pos);
        }
    }
}
