package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.dimension.NebulaPortal;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Заселение Гимпанума — своё, вместо ванильного.
 *
 * <p>Ванильный заселитель для такого измерения не годится в двух местах сразу,
 * и обойти это настройкой биома нельзя.
 *
 * <p><b>Первое — общий предел.</b> Игра держит не больше 70 враждебных мобов на
 * 289 чанков, то есть примерно четверть моба на чанк, и делит их между всеми
 * записями биома пропорционально весу. «Один призрак на чанк» — вчетверо больше
 * всего этого запаса, и никакими весами такого не добиться: вес решает, кому
 * достанется доля, а не насколько велик пирог.
 *
 * <p><b>Второе — выбор высоты.</b> Точка для попытки берётся на случайной высоте
 * от дна мира до поверхности. В Гимпануме под барханами лежит лабиринт в
 * полсотни блоков, поэтому почти каждая точка приходится на подземелье: попытки
 * поставить Ходока на поверхность проваливались одна за другой, а Призраку
 * годилась любая, и он выедал долю остальных. Отсюда и то, что видно в игре, —
 * пустая пустыня и битком набитый лабиринт.
 *
 * <p>Поэтому список мобов в биоме пуст, а расселением занимается этот класс.
 * Он идёт по чанкам вокруг игрока, считает, кто там уже есть, и добирает до
 * заданной плотности — то есть задаёт плотность прямо, а не через доли общего
 * предела. Ставит не ближе {@link #KEEP_AWAY} блоков и не больше
 * {@link #PER_PASS} штук за заход, чтобы население набиралось за несколько
 * секунд, а не одним рывком.
 *
 * <p>Убирает мобов по-прежнему игра: всё, что отошло от игрока дальше 128
 * блоков, исчезает само. Поэтому население держится около заданного и не растёт
 * бесконечно.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class GimpanumSpawner {

    /**
     * Сколько чанков вокруг игрока заселяем.
     *
     * <p>Четыре — это 64 блока, дальность, на которой мобы вообще
     * отрисовываются. Ставить дальше незачем: их не увидят, а тикать они будут.
     */
    private static final int RADIUS = 4;

    /** Ходоков на чанк поверхности. */
    private static final int WALKERS_PER_CHUNK = 2;

    /** Одна Молния на столько Ходоков. */
    private static final int WALKERS_PER_BOLT = 50;

    /** Один Призрак на столько чанков лабиринта. */
    private static final int CHUNKS_PER_WRAITH = 4;

    /** Один Поглотитель на столько чанков, независимо от высоты. */
    private static final int CHUNKS_PER_DEVOURER = 200;

    /** Раз в две секунды. */
    private static final int PERIOD = 40;

    /** Ближе этого к игроку никто не появляется. */
    private static final int KEEP_AWAY = 24;

    /** Появлений за один заход — чтобы население набиралось плавно. */
    private static final int PER_PASS = 12;

    /** Попыток найти место под одного моба. */
    private static final int TRIES = 3;

    /** Запас от кромки барханов, ниже которого начинается лабиринт. */
    private static final int DEPTH = 7;

    private static final long WRAITH_SALT = 0x7A3B91C6L;
    private static final long BOLT_SALT = 0x51ED270BL;
    private static final long DEVOURER_SALT = 0x2F1E3C4DL;

    /** Пустой счёт для слоя, который сейчас не заселяем. */
    private static final Long2IntMap EMPTY = new Long2IntOpenHashMap();

    private GimpanumSpawner() {
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        ServerLevel level = server.getLevel(NebulaPortal.GIMPANUM);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        int budget = PER_PASS;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            budget = populate(level, player.chunkPosition(), onSurface(level, player), budget);
            if (budget <= 0) {
                return;
            }
        }
    }

    /**
     * Добрать население вокруг одного игрока.
     *
     * <p>Слой выбирается по самому игроку: стоящему на барханах достаются
     * Ходоки и Молнии, спустившемуся в лабиринт — Призраки. Это не поблажка
     * ради нагрузки, а то же самое, что и в замысле: Призрак живёт у дна и сам
     * туда возвращается ({@link com.kirbornu.gimpanum.entity.goal.SinkToDepthsGoal}),
     * а Ходок с бархана вниз не спускается. Держать полсотни Призраков под
     * ногами у того, кто гуляет по поверхности, значило бы тикать ими впустую.
     *
     * <p>Поглотитель не привязан ни к какому слою и добирается всегда.
     */
    private static int populate(ServerLevel level, ChunkPos centre, boolean surface, int budget) {
        AABB region = new AABB(
                (centre.x - RADIUS) << 4, level.getMinBuildHeight(), (centre.z - RADIUS) << 4,
                (centre.x + RADIUS + 1) << 4, level.getMaxBuildHeight(), (centre.z + RADIUS + 1) << 4);

        int side = 2 * RADIUS + 1;
        int chunks = side * side;

        // Какие чанки несут одиночек. Считаем заранее: это же число задаёт и
        // предел на всю область, а не только ответ по каждому чанку.
        boolean[] withWraith = new boolean[chunks];
        boolean[] withBolt = new boolean[chunks];
        boolean[] withDevourer = new boolean[chunks];
        int wraithQuota = 0;
        int boltQuota = 0;
        int devourerQuota = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int i = (dx + RADIUS) * side + (dz + RADIUS);
                if (!surface && carries(level, centre.x + dx, centre.z + dz,
                        CHUNKS_PER_WRAITH, WRAITH_SALT)) {
                    withWraith[i] = true;
                    wraithQuota++;
                }
                if (surface && carries(level, centre.x + dx, centre.z + dz,
                        WALKERS_PER_BOLT / WALKERS_PER_CHUNK, BOLT_SALT)) {
                    withBolt[i] = true;
                    boltQuota++;
                }
                if (carries(level, centre.x + dx, centre.z + dz, CHUNKS_PER_DEVOURER, DEVOURER_SALT)) {
                    withDevourer[i] = true;
                    devourerQuota++;
                }
            }
        }

        Long2IntMap walkers = surface ? count(level, GimpanumEntities.DUNE_WALKER.get(), region) : EMPTY;
        Long2IntMap bolts = surface ? count(level, GimpanumEntities.PLASMA_BOLT.get(), region) : EMPTY;
        Long2IntMap wraiths = surface ? EMPTY : count(level, GimpanumEntities.COMET_WRAITH.get(), region);
        Long2IntMap devourers = count(level, GimpanumEntities.SPACE_DEVOURER.get(), region);

        // Предел на всю область поверх предела на чанк. Без него население
        // медленно ползёт вверх: мобы расходятся по соседям, опустевший чанк
        // просит добавки, а ушедшие никуда не делись.
        int walkerRoom = surface ? WALKERS_PER_CHUNK * chunks - total(walkers) : 0;
        int boltRoom = surface ? boltQuota - total(bolts) : 0;
        int wraithRoom = surface ? 0 : wraithQuota - total(wraiths);
        int devourerRoom = devourerQuota - total(devourers);

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (budget <= 0) {
                    return 0;
                }
                int cx = centre.x + dx;
                int cz = centre.z + dz;
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    continue;
                }
                long key = ChunkPos.asLong(cx, cz);
                int i = (dx + RADIUS) * side + (dz + RADIUS);

                int added = fill(level, cx, cz, GimpanumEntities.DUNE_WALKER.get(),
                        Math.min(WALKERS_PER_CHUNK - walkers.get(key), walkerRoom), budget,
                        GimpanumSpawner::dunes);
                walkerRoom -= added;
                budget -= added;

                if (withBolt[i]) {
                    added = fill(level, cx, cz, GimpanumEntities.PLASMA_BOLT.get(),
                            Math.min(1 - bolts.get(key), boltRoom), budget, GimpanumSpawner::sky);
                    boltRoom -= added;
                    budget -= added;
                }

                if (withWraith[i]) {
                    added = fill(level, cx, cz, GimpanumEntities.COMET_WRAITH.get(),
                            Math.min(1 - wraiths.get(key), wraithRoom), budget,
                            GimpanumSpawner::labyrinth);
                    wraithRoom -= added;
                    budget -= added;
                }

                if (withDevourer[i]) {
                    added = fill(level, cx, cz, GimpanumEntities.SPACE_DEVOURER.get(),
                            Math.min(1 - devourers.get(key), devourerRoom), budget,
                            GimpanumSpawner::anywhere);
                    devourerRoom -= added;
                    budget -= added;
                }
            }
        }
        return budget;
    }

    /** Сколько всего мобов такого рода в области. */
    private static int total(Long2IntMap counts) {
        int sum = 0;
        IntIterator it = counts.values().iterator();
        while (it.hasNext()) {
            sum += it.nextInt();
        }
        return sum;
    }

    /** Ищет место, куда встаёт сущность такого размера, и возвращает его — или ничего. */
    @FunctionalInterface
    private interface Spot {
        @Nullable
        Vec3 find(ServerLevel level, int chunkX, int chunkZ, EntityType<?> type, RandomSource random);
    }

    /** Доставить в чанк недостающих мобов. Возвращает, сколько поставлено. */
    private static int fill(ServerLevel level, int chunkX, int chunkZ, EntityType<? extends Mob> type,
                            int missing, int budget, Spot spot) {
        int done = 0;
        RandomSource random = level.random;
        while (done < missing && done < budget) {
            Vec3 at = null;
            for (int attempt = 0; attempt < TRIES && at == null; attempt++) {
                at = spot.find(level, chunkX, chunkZ, type, random);
            }
            if (at == null || !spawn(level, type, at)) {
                break;
            }
            done++;
        }
        return done;
    }

    /**
     * Чанк, который всегда несёт одного такого моба.
     *
     * <p>Зерно замешано из зерна мира и координат чанка, как это делает игра при
     * размещении структур: один и тот же чанк всегда даёт один и тот же ответ,
     * и «раз в тридцать чанков» означает ровно это, а не «с вероятностью один к
     * тридцати каждый заход».
     */
    private static boolean carries(ServerLevel level, int chunkX, int chunkZ, int oneIn, long salt) {
        if (oneIn <= 1) {
            return true;
        }
        RandomSource random = RandomSource.create(
                chunkX * 341873128712L + chunkZ * 132897987541L + level.getSeed() + salt);
        return random.nextInt(oneIn) == 0;
    }

    /** Кромка барханов: Ходоки. */
    @Nullable
    private static Vec3 dunes(ServerLevel level, int chunkX, int chunkZ, EntityType<?> type, RandomSource random) {
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!level.getBlockState(new BlockPos(x, y - 1, z)).is(GimpanumContent.COSMIC_SAND.get())) {
            return null;
        }
        return free(level, type, x + 0.5, y, z + 0.5);
    }

    /** Толща под барханами: Призраки. */
    @Nullable
    private static Vec3 labyrinth(ServerLevel level, int chunkX, int chunkZ, EntityType<?> type, RandomSource random) {
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int floor = level.getMinBuildHeight() + 1;
        int ceiling = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - DEPTH;
        if (ceiling <= floor) {
            return null;
        }
        return free(level, type, x + 0.5, floor + random.nextInt(ceiling - floor), z + 0.5);
    }

    /** Небо над барханами: Молнии. */
    @Nullable
    private static Vec3 sky(ServerLevel level, int chunkX, int chunkZ, EntityType<?> type, RandomSource random) {
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int floor = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1, PlasmaBolt.FLOOR);
        int ceiling = Math.min(PlasmaBolt.CEILING, level.getMaxBuildHeight() - 1) - (int) Math.ceil(type.getHeight());
        if (ceiling <= floor) {
            return null;
        }
        return free(level, type, x + 0.5, floor + random.nextInt(ceiling - floor), z + 0.5);
    }

    /** Любая высота: Поглотители. */
    @Nullable
    private static Vec3 anywhere(ServerLevel level, int chunkX, int chunkZ, EntityType<?> type, RandomSource random) {
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int floor = level.getMinBuildHeight() + 1;
        int ceiling = level.getMaxBuildHeight() - (int) Math.ceil(type.getHeight()) - 1;
        if (ceiling <= floor) {
            return null;
        }
        return free(level, type, x + 0.5, floor + random.nextInt(ceiling - floor), z + 0.5);
    }

    /**
     * Точка, если сущность туда помещается и там не стоит игрок.
     *
     * <p>Габарит собираем руками, а не берём готовый: у Поглотителя четыре
     * блока в ширину и три в высоту, и проверять его по одной клетке — то же
     * самое, что не проверять вовсе.
     */
    @Nullable
    private static Vec3 free(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        double half = type.getWidth() / 2.0;
        AABB box = new AABB(x - half, y, z - half, x + half, y + type.getHeight(), z + half);
        if (!level.noCollision(box)) {
            return null;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(x, y, z) < KEEP_AWAY * KEEP_AWAY) {
                return null;
            }
        }
        return new Vec3(x, y, z);
    }

    private static boolean spawn(ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(level);
        if (mob == null) {
            return false;
        }
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        // Через EventHooks, а не напрямую: так о появлении узнают чужие моды —
        // прямой вызов finalizeSpawn в NeoForge для того и объявлен устаревшим.
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                MobSpawnType.NATURAL, null);
        return level.addFreshEntity(mob);
    }

    /** Сколько мобов такого рода уже стоит в каждом чанке области. */
    private static Long2IntMap count(ServerLevel level, EntityType<? extends Mob> type, AABB region) {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        for (Entity entity : level.getEntities(type, region, e -> true)) {
            counts.addTo(ChunkPos.asLong(entity.blockPosition()), 1);
        }
        return counts;
    }

    /** Игрок на барханах, а не в лабиринте под ними. */
    private static boolean onSurface(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return pos.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - DEPTH;
    }
}
