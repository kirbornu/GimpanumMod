package com.kirbornu.gimpanum.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Снимок положения блока, стоящего на физической конструкции Sable.
 *
 * <p>Намеренно не содержит ни одного типа из Sable: этот класс безопасно
 * загружать и без установленного мода.
 *
 * <p>Связь координат проверена на живом сервере 2026-08-01: мировую позицию
 * даёт {@code pose.transformPosition(rawCenter)}, потому что {@code rotationPoint}
 * задан в тех же сырых координатах делянки. Вариант «отсчёт от центра делянки»
 * проверен и отвергнут — промах на 22 млн блоков.
 *
 * @param subLevelId       UUID конструкции
 * @param name             имя конструкции; у безымянных конструкций пусто
 * @param plotPos          чанк-координата «делянки» в служебном регионе
 * @param rawPos           позиция блока в координатах делянки (то, что вернёт getBlockPos)
 * @param rawCenter        центр блока в координатах делянки
 * @param worldCenter      центр блока в мировых координатах — где игрок его видит
 * @param posePosition     позиция конструкции
 * @param poseRotationPoint точка вращения из позы
 * @param poseScale        масштаб из позы
 */
public record SubLevelInfo(
        UUID subLevelId,
        Optional<String> name,
        ChunkPos plotPos,
        BlockPos rawPos,
        Vec3 rawCenter,
        Vec3 worldCenter,
        Vec3 posePosition,
        Vec3 poseRotationPoint,
        Vec3 poseScale
) {
    /** Имя конструкции для показа человеку. */
    public String displayName() {
        return name.orElse("<без имени>");
    }
}
