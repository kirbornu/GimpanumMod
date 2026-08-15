package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;

/** Куда и как переносит портальная арка. */
public final class NebulaPortal {

    public static final ResourceKey<Level> GIMPANUM =
            ResourceKey.create(Registries.DIMENSION, Gimpanum.id("gimpanum"));

    /** Ключ структуры — по нему портал ищется мирогеном, если ни один ещё не посещён. */
    public static final ResourceKey<Structure> STRUCTURE =
            ResourceKey.create(Registries.STRUCTURE, Gimpanum.id("nebula_portal"));

    /**
     * Радиус поиска структуры в чанках.
     *
     * <p>Порталы стоят через 125 чанков, поэтому меньший радиус их просто не
     * застанет. Поиск идёт только когда список известных порталов пуст.
     */
    private static final int SEARCH_CHUNKS = 160;

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
     * каждый вход — лотерея среди всех известных выходов.
     */
    public static void teleport(ServerLevel from, Entity entity) {
        MinecraftServer server = from.getServer();
        ResourceKey<Level> targetKey = other(from.dimension());
        ServerLevel target = server.getLevel(targetKey);
        if (target == null) {
            Gimpanum.LOGGER.error("Портал: измерение {} не найдено", targetKey.location());
            return;
        }

        Optional<BlockPos> destination = pick(server, target, entity.getRandom().nextInt());
        if (destination.isEmpty()) {
            Gimpanum.LOGGER.warn("Портал: в {} не найдено ни одного портала", targetKey.location());
            return;
        }

        BlockPos pos = destination.get();
        // Ставим рядом с аркой, а не в её плоскость: иначе игрока тут же
        // утянуло бы обратно.
        BlockPos spot = pos.offset(0, 1, 3);
        entity.setPortalCooldown();
        entity.teleportTo(target, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                java.util.Set.of(), entity.getYRot(), entity.getXRot());
    }

    /**
     * Случайный портал на той стороне.
     *
     * <p>Сначала — среди уже посещённых: это дёшево и не грузит чанки. Если
     * таких нет, портал ищется мирогеном от случайной точки; поиск дорогой, но
     * случается только один раз за измерение.
     */
    private static Optional<BlockPos> pick(MinecraftServer server, ServerLevel target, int seed) {
        List<BlockPos> known = PortalIndex.in(server, target.dimension());
        if (!known.isEmpty()) {
            return Optional.of(known.get(Math.floorMod(seed, known.size())));
        }
        return search(target, seed);
    }

    private static Optional<BlockPos> search(ServerLevel target, int seed) {
        try {
            var registry = target.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var holder = registry.getHolder(STRUCTURE).orElse(null);
            if (holder == null) {
                return Optional.empty();
            }
            // Поиск от случайной точки: ближайший к ней портал и будет случайным
            // среди всех, а не всегда одним и тем же.
            java.util.Random rnd = new java.util.Random(seed);
            BlockPos from = new BlockPos(rnd.nextInt(40000) - 20000, 64, rnd.nextInt(40000) - 20000);
            var found = target.getChunkSource().getGenerator().findNearestMapStructure(
                    target, net.minecraft.core.HolderSet.direct(holder), from, SEARCH_CHUNKS, false);
            return found == null ? Optional.empty() : Optional.of(found.getFirst());
        } catch (Throwable t) {
            Gimpanum.LOGGER.error("Портал: поиск структуры сорвался", t);
            return Optional.empty();
        }
    }
}
