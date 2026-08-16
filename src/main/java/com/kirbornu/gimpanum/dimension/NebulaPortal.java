package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Куда и как переносит портальная арка.
 *
 * <p>Выход всегда случайный, и — главное — он не обязан быть уже посещённым.
 * Размещение структур в игре подчиняется сетке: зная зерно мира и параметры
 * сетки, чанк-кандидат вычисляется без всякой загрузки. Мы выбираем случайную
 * ячейку этой сетки, дотягиваем её чанк до готового вида и ищем в нём
 * настоящий блок портала.
 *
 * <p>Так было не всегда: раньше выход искался поиском ближайшей структуры,
 * который возвращает <i>начало</i> структуры — угол её чанка на нулевой
 * высоте. Отсюда и брались переносы в коренную породу.
 *
 * <p>Границу мира проверяем отдельно и дважды — при выборе выхода и при
 * выборе площадки. Сетка размещения о границе не знает и спокойно предложит
 * ячейку за ней; выйти оттуда игрок уже не сможет.
 */
public final class NebulaPortal {

    public static final ResourceKey<Level> GIMPANUM =
            ResourceKey.create(Registries.DIMENSION, Gimpanum.id("gimpanum"));

    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, Gimpanum.id("nebula_portal"));

    private static final ResourceKey<StructureSet> STRUCTURE_SET =
            ResourceKey.create(Registries.STRUCTURE_SET, Gimpanum.id("nebula_portal"));

    /** Сколько ячеек сетки перебрать, прежде чем сдаться. */
    private static final int ATTEMPTS = 12;

    /** В скольких ячейках сетки от начала координат искать выход. */
    private static final int REGION_RANGE = 24;

    /** Радиус поиска площадки у выхода. */
    private static final int FOOTING_RADIUS = 6;

    private NebulaPortal() {
    }

    /** Куда ведёт портал из этого измерения: Гимпанум и верхний мир — пара. */
    public static ResourceKey<Level> other(ResourceKey<Level> from) {
        return GIMPANUM.equals(from) ? Level.OVERWORLD : GIMPANUM;
    }

    /**
     * Переносит существо к случайному порталу на той стороне.
     *
     * <p>Случайному, а не парному: связи «этот к тому» нет по замыслу, поэтому
     * каждый вход — лотерея среди всех выходов, посещённых и нет.
     */
    public static void teleport(ServerLevel from, Entity entity) {
        MinecraftServer server = from.getServer();
        ResourceKey<Level> targetKey = other(from.dimension());
        ServerLevel target = server.getLevel(targetKey);
        if (target == null) {
            Gimpanum.LOGGER.error("Портал: измерение {} не найдено", targetKey.location());
            return;
        }

        Optional<BlockPos> destination = pick(server, target, entity.getRandom());
        if (destination.isEmpty()) {
            Gimpanum.LOGGER.warn("Портал: в {} не нашлось ни одного выхода", targetKey.location());
            return;
        }

        BlockPos spot = footing(target, destination.get());
        entity.setPortalCooldown();
        entity.teleportTo(target, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                Set.of(), entity.getYRot(), entity.getXRot());
    }

    /** Случайный выход: сперва по сетке размещения, затем — среди посещённых. */
    private static Optional<BlockPos> pick(MinecraftServer server, ServerLevel target, RandomSource random) {
        Optional<RandomSpreadStructurePlacement> placement = placement(target);
        Optional<Structure> structure = target.registryAccess()
                .registryOrThrow(Registries.STRUCTURE).getOptional(STRUCTURE);

        if (placement.isPresent() && structure.isPresent()) {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                ChunkPos candidate = placement.get().getPotentialStructureChunk(
                        target.getSeed(),
                        random.nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE,
                        random.nextInt(REGION_RANGE * 2 + 1) - REGION_RANGE);
                if (!target.getWorldBorder().isWithinBounds(candidate)) {
                    continue;
                }
                Optional<BlockPos> portal = portalAt(target, candidate, structure.get());
                if (portal.isPresent()) {
                    return portal;
                }
            }
        }

        List<BlockPos> known = PortalIndex.in(server, target.dimension()).stream()
                .filter(pos -> target.getWorldBorder().isWithinBounds(pos))
                .toList();
        return known.isEmpty()
                ? Optional.empty()
                : Optional.of(known.get(random.nextInt(known.size())));
    }

    private static Optional<RandomSpreadStructurePlacement> placement(ServerLevel target) {
        return target.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getOptional(STRUCTURE_SET)
                .map(StructureSet::placement)
                .filter(RandomSpreadStructurePlacement.class::isInstance)
                .map(RandomSpreadStructurePlacement.class::cast);
    }

    /**
     * Настоящий блок портала в указанном чанке-кандидате.
     *
     * <p>Ищем не перебором всего чанка, а по списку блок-сущностей: у плоскости
     * перехода она есть, и таких блоков в чанке единицы.
     */
    private static Optional<BlockPos> portalAt(ServerLevel target, ChunkPos candidate, Structure structure) {
        try {
            ChunkAccess chunk = target.getChunk(candidate.x, candidate.z, ChunkStatus.FULL, true);
            StructureStart start = target.structureManager()
                    .getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
            if (start == null || !start.isValid()) {
                return Optional.empty();
            }
            BoundingBox box = start.getBoundingBox();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    ChunkAccess piece = target.getChunk(cx, cz, ChunkStatus.FULL, true);
                    for (BlockPos pos : piece.getBlockEntitiesPos()) {
                        if (box.isInside(pos)
                                && piece.getBlockState(pos).is(GimpanumContent.NEBULA_PORTAL.get())) {
                            return Optional.of(pos.immutable());
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Throwable failure) {
            Gimpanum.LOGGER.error("Портал: чанк {} не выдал выхода", candidate, failure);
            return Optional.empty();
        }
    }

    /**
     * Куда поставить пришедшего: ближайшая твёрдая площадка рядом с аркой.
     *
     * <p>Ищем, а не отсчитываем от плоскости на глазок: арку разворачивает
     * мироген, и постоянного смещения «на три блока в сторону» не существует.
     * Поиск идёт снизу вверх и от ближнего к дальнему, поэтому первой находится
     * верхняя ступень лестницы — она к арке ближе всего.
     */
    private static BlockPos footing(ServerLevel target, BlockPos portal) {
        for (int radius = 1; radius <= FOOTING_RADIUS; radius++) {
            for (int dy = 0; dy >= -FOOTING_RADIUS; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos candidate = portal.offset(dx, dy, dz);
                        if (standable(target, candidate)
                                && target.getWorldBorder().isWithinBounds(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        // Не нашлось — ставим над аркой: упасть лучше, чем застрять в плоскости.
        return portal.above(3);
    }

    private static boolean standable(ServerLevel target, BlockPos pos) {
        return target.getBlockState(pos).isAir()
                && target.getBlockState(pos.above()).isAir()
                && target.getBlockState(pos.below()).isSolidRender(target, pos.below());
    }
}
