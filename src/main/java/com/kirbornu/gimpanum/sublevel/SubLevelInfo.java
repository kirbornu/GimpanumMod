package com.kirbornu.gimpanum.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Снимок положения блока, стоящего на физической конструкции Sable.
 *
 * <p>Намеренно не содержит ни одного типа из Sable: этот класс безопасно
 * загружать и без установленного мода.
 *
 * @param subLevelId       UUID конструкции
 * @param name             имя конструкции, если задано
 * @param plotPos          чанк-координата «делянки» в служебном регионе
 * @param rawCenter        центр блока в координатах делянки (то, что вернёт getBlockPos)
 * @param worldCenter      центр блока в мировых координатах — где игрок его видит
 * @param plotCenterCandidate альтернативный кандидат: трансформация относительно центра
 *                            делянки. Нужен только зонду, чтобы проверить, какое из
 *                            пространств Sable считает локальным.
 * @param posePosition     позиция конструкции
 * @param poseRotationPoint точка вращения из позы
 * @param poseScale        масштаб из позы
 */
public record SubLevelInfo(
        UUID subLevelId,
        String name,
        ChunkPos plotPos,
        BlockPos rawPos,
        Vec3 rawCenter,
        Vec3 worldCenter,
        Vec3 plotCenterCandidate,
        Vec3 posePosition,
        Vec3 poseRotationPoint,
        Vec3 poseScale
) {
}
