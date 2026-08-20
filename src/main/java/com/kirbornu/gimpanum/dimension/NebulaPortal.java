package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
    private static final int FOOTING_RADIUS = 8;

    /**
     * Насколько далеко от плоскости перехода ставить пришедшего.
     *
     * <p>Три блока, а не один. Раньше площадка искалась с ближайшего кольца, и
     * вышедший оказывался вплотную к плоскости, внутри самой арки: шаг в любую
     * сторону — и он снова в портале. Три блока выносят его на лестницу, за
     * пределы арки.
     */
    private static final int MIN_GAP = 3;

    /** Сколько клеток плоскости обходить, собирая её целиком: в арке их полсотни. */
    private static final int PLANE_LIMIT = 128;

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

        List<BlockPos> plane = plane(target, destination.get());
        BlockPos spot = footing(target, plane);
        // Не разговорчивость, а следы: когда выход окажется не там, где ждали,
        // без этих трёх чисел причину искать нечем.
        Gimpanum.LOGGER.debug("Портал: выход {}, подножие плоскости {}, площадка {}",
                destination.get(), anchor(plane), spot);
        entity.setPortalCooldown();
        entity.teleportTo(target, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                Set.of(), facingAway(plane, spot), entity.getXRot());
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
     * Вся плоскость перехода, а не одна её клетка.
     *
     * <p>Клетку нам дают какую придётся — какая первой попалась в списке
     * блок-сущностей чанка, хоть самую верхнюю. Отмерять от неё три блока
     * бессмысленно: три блока вниз от верхнего края плоскости — это по-прежнему
     * плоскость. Поэтому обходим её целиком и меряем расстояние до ближайшей
     * клетки.
     */
    private static List<BlockPos> plane(ServerLevel target, BlockPos start) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && found.size() < PLANE_LIMIT) {
            BlockPos pos = queue.poll();
            if (!target.getBlockState(pos).is(GimpanumContent.NEBULA_PORTAL.get())) {
                continue;
            }
            found.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return found.isEmpty() ? List.of(start.immutable()) : found;
    }

    /**
     * Куда поставить пришедшего: твёрдая площадка поодаль от арки.
     *
     * <p>Ищем, а не отсчитываем на глазок: арку разворачивает мироген, и
     * постоянного смещения «на три блока в сторону» не существует. Зато есть
     * требование — не ближе {@value #MIN_GAP} блоков к любой клетке плоскости.
     * Если такого места не нашлось, требование ослабляем по шагу: оказаться
     * вплотную всё же лучше, чем повиснуть над аркой.
     */
    private static BlockPos footing(ServerLevel target, List<BlockPos> plane) {
        BlockPos centre = anchor(plane);
        for (int gap = MIN_GAP; gap >= 1; gap--) {
            // Сперва перебираем всё на одном уровне и лишь потом спускаемся:
            // выйти в трёх шагах от арки лучше, чем в двух, но восемью
            // блоками ниже — по такой площадке ещё карабкаться обратно.
            for (int dy = 0; dy >= -FOOTING_RADIUS; dy--) {
                for (int radius = gap; radius <= FOOTING_RADIUS; radius++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                                continue;
                            }
                            BlockPos candidate = centre.offset(dx, dy, dz);
                            if (distance(plane, candidate) < gap) {
                                continue;
                            }
                            if (standable(target, candidate)
                                    && target.getWorldBorder().isWithinBounds(candidate)) {
                                return candidate;
                            }
                        }
                    }
                }
            }
        }
        // Не нашлось вовсе — ставим над аркой: упасть лучше, чем застрять в плоскости.
        return centre.above(3);
    }

    /**
     * Подножие плоскости — её нижний ряд, середина.
     *
     * <p>Отмерять от какой попало клетки нельзя: список приходит от обхода, а
     * тот начинается с той клетки, которую первой выдал чанк, — легко с самой
     * верхней. От верхней «ноль по высоте» — это крыша арки, и пришедший
     * оказывался на ней. От подножия ноль — это пол перед аркой.
     */
    private static BlockPos anchor(List<BlockPos> plane) {
        int floor = Integer.MAX_VALUE;
        for (BlockPos cell : plane) {
            floor = Math.min(floor, cell.getY());
        }
        long x = 0;
        long z = 0;
        int count = 0;
        for (BlockPos cell : plane) {
            if (cell.getY() == floor) {
                x += cell.getX();
                z += cell.getZ();
                count++;
            }
        }
        return new BlockPos((int) (x / count), floor, (int) (z / count));
    }

    /** Расстояние по клеткам до ближайшей клетки плоскости. */
    private static int distance(List<BlockPos> plane, BlockPos pos) {
        int nearest = Integer.MAX_VALUE;
        for (BlockPos cell : plane) {
            int d = Math.max(Math.max(Math.abs(cell.getX() - pos.getX()), Math.abs(cell.getY() - pos.getY())),
                    Math.abs(cell.getZ() - pos.getZ()));
            nearest = Math.min(nearest, d);
        }
        return nearest;
    }

    /**
     * Развернуть пришедшего спиной к арке.
     *
     * <p>Мелочь, но именно она решает: тот, кто вышел лицом в плоскость,
     * шагает вперёд не глядя и уезжает обратно.
     */
    private static float facingAway(List<BlockPos> plane, BlockPos spot) {
        double x = 0.0;
        double z = 0.0;
        for (BlockPos cell : plane) {
            x += cell.getX();
            z += cell.getZ();
        }
        double dx = spot.getX() - x / plane.size();
        double dz = spot.getZ() - z / plane.size();
        if (dx == 0.0 && dz == 0.0) {
            return 0.0F;
        }
        return (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
    }

    private static boolean standable(ServerLevel target, BlockPos pos) {
        return target.getBlockState(pos).isAir()
                && target.getBlockState(pos.above()).isAir()
                && target.getBlockState(pos.below()).isSolidRender(target, pos.below());
    }
}
