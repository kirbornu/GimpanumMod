package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * В вакууме баллон не накачаешь.
 *
 * <p>Create позволяет заправить баллон, подав на поставленный блок вращение:
 * по смыслу компрессор набирает воздух из окружающего мира. В Гимпануме
 * набирать нечего, иначе костюм с запасом на четверть часа не значит ничего —
 * достаточно привезти вал.
 *
 * <p>Запас не обнуляем, а лишь запрещаем ему расти: привезённый полным баллон
 * останется полным, даже если его поставить. Компрессор при этом шипит и
 * пылит — иначе игрок долго не поймёт, почему шкала стоит на месте.
 *
 * <p>Ни одного класса Create тут не упомянуто: тип блок-сущности ищется по
 * имени в реестре, методы — рефлексией по найденному экземпляру. Без Create
 * обработчик просто ничего не находит.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class BacktankVent {

    private static final ResourceLocation BACKTANK =
            ResourceLocation.fromNamespaceAndPath("create", "backtank");

    /** Осмотр раз в полсекунды: за это время компрессор успевает набрать немного, и мы это отнимаем. */
    private static final int SCAN_INTERVAL = 10;

    private static boolean typeResolved;
    private static BlockEntityType<?> backtankType;
    private static Method getAirLevel;
    private static Method setAirLevel;
    private static boolean reflectionFailed;

    /** Сколько воздуха было в каждом баллоне при прошлом осмотре. */
    private static Map<BlockPos, Integer> lastSeen = new HashMap<>();

    private BacktankVent() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !NebulaPortal.GIMPANUM.equals(level.dimension())
                || level.getGameTime() % SCAN_INTERVAL != 0) {
            return;
        }
        BlockEntityType<?> type = type();
        if (type == null || reflectionFailed) {
            return;
        }

        LongSet chunks = candidates(level);
        if (chunks.isEmpty()) {
            if (!lastSeen.isEmpty()) {
                lastSeen = new HashMap<>();
            }
            return;
        }

        Map<BlockPos, Integer> current = new HashMap<>();
        LongIterator it = chunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity.getType() != type) {
                    continue;
                }
                Integer air = airOf(blockEntity);
                if (air == null) {
                    return;
                }
                BlockPos pos = blockEntity.getBlockPos().immutable();
                Integer before = lastSeen.get(pos);
                if (before != null && air > before) {
                    setAir(blockEntity, before);
                    air = before;
                    vent(level, pos);
                }
                current.put(pos, air);
            }
        }
        lastSeen = current;
    }

    /** Чанки, где блок-сущность вообще может тикать: вокруг игроков и принудительно загруженные. */
    private static LongSet candidates(ServerLevel level) {
        LongSet chunks = new LongOpenHashSet();
        int radius = level.getServer().getPlayerList().getSimulationDistance();
        for (ServerPlayer player : level.players()) {
            ChunkPos centre = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    chunks.add(ChunkPos.asLong(centre.x + dx, centre.z + dz));
                }
            }
        }
        chunks.addAll(level.getForcedChunks());
        return chunks;
    }

    private static void vent(ServerLevel level, BlockPos pos) {
        // Не на каждый осмотр, иначе шипение сливается в сплошной шум.
        if (level.getGameTime() % 40 >= SCAN_INTERVAL) {
            return;
        }
        level.sendParticles(ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                3, 0.15, 0.05, 0.15, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.6F);
    }

    private static BlockEntityType<?> type() {
        if (!typeResolved) {
            typeResolved = true;
            backtankType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(BACKTANK);
        }
        return backtankType;
    }

    private static Integer airOf(BlockEntity blockEntity) {
        if (!resolve(blockEntity)) {
            return null;
        }
        try {
            Object value = getAirLevel.invoke(blockEntity);
            return value instanceof Integer air ? air : null;
        } catch (Throwable failure) {
            fail(failure);
            return null;
        }
    }

    private static void setAir(BlockEntity blockEntity, int air) {
        try {
            setAirLevel.invoke(blockEntity, air);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static boolean resolve(BlockEntity blockEntity) {
        if (getAirLevel != null) {
            return true;
        }
        try {
            Class<?> owner = blockEntity.getClass();
            getAirLevel = owner.getMethod("getAirLevel");
            setAirLevel = owner.getMethod("setAirLevel", int.class);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    private static void fail(Throwable failure) {
        reflectionFailed = true;
        Gimpanum.LOGGER.warn("Не удалось добраться до запаса воздуха в баллоне Create — "
                + "в Гимпануме его можно будет заправлять", failure);
    }
}
