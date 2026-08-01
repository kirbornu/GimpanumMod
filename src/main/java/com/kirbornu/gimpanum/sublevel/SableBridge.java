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

    static Optional<SubLevelInfo> describe(Level level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) {
            // Блок в обычном мире, а не в служебном регионе конструкций.
            return Optional.empty();
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) {
            return Optional.empty();
        }

        SubLevel subLevel = plot.getSubLevel();
        if (subLevel == null || subLevel.isRemoved()) {
            return Optional.empty();
        }

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
