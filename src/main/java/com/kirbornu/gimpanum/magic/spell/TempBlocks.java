package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Временные блоки заклинаний и когда их убрать.
 *
 * <p>Записи живут на диске, а не в сущности-заклинании: сервер может
 * перезапуститься посреди минуты, пока горит магический свет, и тогда без
 * записи свет остался бы навсегда. Время — игровое, поэтому оно не идёт, пока
 * сервер выключен, и блок исчезнет через те же шестьдесят секунд игры.
 *
 * <p>Блок убирается, только если он всё ещё тот, что ставило заклинание. Если
 * его успели сломать или заменить, запись просто вычёркивается: стирать чужую
 * постройку ради порядка в своих записях нельзя.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class TempBlocks extends SavedData {

    private static final String FILE_NAME = "gimpanum_magic_temp_blocks";
    private static final String KEY_LIST = "Blocks";

    /** Раз в секунду: точнее для света не нужно, а чаще — зря листать список. */
    private static final int CHECK_INTERVAL = 20;

    private record Entry(ResourceKey<Level> dimension, BlockPos pos, BlockState expected, BlockState restore,
                         long expiresAt) {
    }

    private final List<Entry> entries = new ArrayList<>();

    private TempBlocks() {
    }

    private static TempBlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TempBlocks::new, TempBlocks::load, null), FILE_NAME);
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /**
     * Запомнить, что через {@code ticks} блок {@code expected} надо вернуть к {@code restore}.
     */
    public static void add(ServerLevel level, BlockPos pos, BlockState expected, BlockState restore, long ticks) {
        TempBlocks data = get(level.getServer());
        data.entries.add(new Entry(level.dimension(), pos.immutable(), expected, restore,
                now(level.getServer()) + ticks));
        data.setDirty();
    }

    /** Убрать сейчас же, не дожидаясь срока, — если блок всё ещё наш. */
    public static void removeNow(ServerLevel level, BlockPos pos, BlockState expected) {
        TempBlocks data = get(level.getServer());
        Iterator<Entry> it = data.entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.dimension().equals(level.dimension()) && entry.pos().equals(pos)
                    && entry.expected().equals(expected)) {
                restore(level, entry);
                it.remove();
                data.setDirty();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        TempBlocks data = get(server);
        if (data.entries.isEmpty()) {
            return;
        }
        long now = now(server);
        Iterator<Entry> it = data.entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.expiresAt() > now) {
                continue;
            }
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) {
                // Измерение пропало из мира — блок убрать негде, запись бессмысленна.
                it.remove();
                data.setDirty();
                continue;
            }
            if (!level.isLoaded(entry.pos())) {
                // Подгружать чанк ради одного блока нельзя: уберём, когда загрузится.
                continue;
            }
            restore(level, entry);
            it.remove();
            data.setDirty();
        }
    }

    private static void restore(ServerLevel level, Entry entry) {
        if (level.isLoaded(entry.pos()) && level.getBlockState(entry.pos()).equals(entry.expected())) {
            level.setBlock(entry.pos(), entry.restore(), Block.UPDATE_ALL);
        }
    }

    private static TempBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        TempBlocks data = new TempBlocks();
        HolderLookup<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList(KEY_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag stored = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(stored.getString("Dimension"));
            int[] pos = stored.getIntArray("Pos");
            if (dimension == null || pos.length != 3) {
                continue;
            }
            data.entries.add(new Entry(ResourceKey.create(Registries.DIMENSION, dimension),
                    new BlockPos(pos[0], pos[1], pos[2]),
                    NbtUtils.readBlockState(blocks, stored.getCompound("Expected")),
                    NbtUtils.readBlockState(blocks, stored.getCompound("Restore")),
                    stored.getLong("ExpiresAt")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag stored = new CompoundTag();
            stored.putString("Dimension", entry.dimension().location().toString());
            stored.putIntArray("Pos", new int[]{entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()});
            stored.put("Expected", NbtUtils.writeBlockState(entry.expected()));
            stored.put("Restore", NbtUtils.writeBlockState(entry.restore()));
            stored.putLong("ExpiresAt", entry.expiresAt());
            list.add(stored);
        }
        tag.put(KEY_LIST, list);
        return tag;
    }
}
