package com.kirbornu.gimpanum.sublevel;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.Optional;

/**
 * Единственный класс мода, который напрямую ссылается на типы Sable.
 *
 * <p>Изоляция намеренная: JVM загружает класс лениво, поэтому пока
 * {@link SubLevelSupport} не вызовет отсюда метод, отсутствие Sable ничем не
 * грозит. Не обращаться к этому классу, минуя {@link SubLevelSupport}.
 */
final class SableBridge {

    private SableBridge() {
    }

    /**
     * Только мировая позиция, без сбора остальных сведений.
     *
     * <p>Отдельно от {@link #describe} потому, что это горячий путь: позиция
     * нужна каждому Ядру по нескольку раз в секунду ради частиц и выдачи
     * предметов, а полное описание тянет за собой лишние объекты.
     *
     * @return {@code null}, если блок стоит в обычном мире
     */
    static Vec3 worldCenterOrNull(Level level, BlockPos pos) {
        SubLevel subLevel = subLevelAt(level, pos);
        return subLevel == null ? null : subLevel.logicalPose().transformPosition(Vec3.atCenterOf(pos));
    }

    private static SubLevel subLevelAt(Level level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) {
            return null;
        }
        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) {
            return null;
        }
        SubLevel subLevel = plot.getSubLevel();
        return subLevel == null || subLevel.isRemoved() ? null : subLevel;
    }

    static Optional<SubLevelInfo> describe(Level level, BlockPos pos) {
        SubLevel subLevel = subLevelAt(level, pos);
        if (subLevel == null) {
            // Блок в обычном мире, а не в служебном регионе конструкций.
            return Optional.empty();
        }
        LevelPlot plot = subLevel.getPlot();

        Pose3dc pose = subLevel.logicalPose();
        Vec3 rawCenter = Vec3.atCenterOf(pos);

        // Координаты делянки — и есть локальное пространство позы: rotationPoint
        // задан в них же. Проверено на живом сервере, см. SubLevelInfo.
        Vec3 worldCenter = pose.transformPosition(rawCenter);

        return Optional.of(new SubLevelInfo(
                subLevel.getUniqueId(),
                Optional.ofNullable(subLevel.getName()),
                plot.plotPos,
                pos,
                rawCenter,
                worldCenter,
                toVec3(pose.position()),
                toVec3(pose.rotationPoint()),
                toVec3(pose.scale())
        ));
    }

    private static Vec3 toVec3(Vector3dc v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
