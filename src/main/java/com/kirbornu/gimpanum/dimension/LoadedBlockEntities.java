package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.function.Consumer;

/**
 * Обход блок-сущностей Гимпанума.
 *
 * <p>Ни один клеймовый или создающий мод не даёт события «блок-сущность
 * протикала», поэтому за чужими блоками приходится присматривать обходом.
 * Обход общий на всех, кто в нём нуждается: пройти чанки один раз и раздать
 * находки — дешевле, чем ходить по тем же чанкам дважды.
 *
 * <p>Границы обхода — все загруженные чанки измерения, а не круг вокруг
 * игроков. Круга мало: блоки физических конструкций Sable лежат в служебном
 * регионе за миллионы блоков от любого игрока, но грузятся и работают вместе
 * с кораблём. Обходить только окрестности игроков значило бы не видеть ничего,
 * что стоит на корабле. Список загруженных чанков ведётся по событиям
 * загрузки и выгрузки: у игры спросить его напрямую нечем.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class LoadedBlockEntities {

    /**
     * Загруженные чанки Гимпанума.
     *
     * <p>Под замком: из какого потока приходят события чанков, NeoForge не
     * обещает, а обход идёт из тика сервера.
     */
    private static final LongSet LOADED = new LongOpenHashSet();

    private LoadedBlockEntities() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Событие приходит и до того, как чанк станет готовым, — поэтому
        // здесь только запоминаем место, а сам чанк спрашиваем при обходе.
        if (event.getLevel() instanceof ServerLevel level && NebulaPortal.GIMPANUM.equals(level.dimension())) {
            synchronized (LOADED) {
                LOADED.add(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && NebulaPortal.GIMPANUM.equals(level.dimension())) {
            synchronized (LOADED) {
                LOADED.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    /** В одиночной игре состояние пережило бы выход в меню. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (LOADED) {
            LOADED.clear();
        }
    }

    /** Есть ли что обходить. */
    public static boolean isEmpty() {
        synchronized (LOADED) {
            return LOADED.isEmpty();
        }
    }

    /**
     * Отдаёт каждую блок-сущность загруженных чанков Гимпанума.
     *
     * <p>Чанк, которого в памяти уже или ещё нет в готовом виде, пропускается.
     * Обходим копию списка: посетитель вправе менять мир, а с ним и загрузку
     * чанков.
     */
    public static void forEach(ServerLevel level, Consumer<BlockEntity> visitor) {
        LongSet snapshot;
        synchronized (LOADED) {
            snapshot = new LongOpenHashSet(LOADED);
        }
        LongIterator it = snapshot.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values().toArray(BlockEntity[]::new)) {
                visitor.accept(blockEntity);
            }
        }
    }
}
