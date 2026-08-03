package com.kirbornu.gimpanum.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Указатель «имя Ядра → где оно сейчас».
 *
 * <p>Существует потому, что адресовать Ядро координатами неудобно: у блока на
 * физической конструкции они выглядят как 20481032, и запомнить их невозможно.
 *
 * <p>Указатель живёт только в памяти и наполняется при загрузке блок-сущностей.
 * Это намеренно: Ядро переезжает при сборке конструкции, его могут скопировать
 * схематикой или снести сторонним модом — сохранённый на диск список неминуемо
 * разошёлся бы с действительностью, а этот самовосстанавливается.
 *
 * <p>Плата за это: выгруженное Ядро по имени не находится. Само имя лежит в NBT
 * блок-сущности и переживает всё, так что при загрузке оно возвращается.
 */
public final class CoreIndex {

    private record Entry(UUID coreId, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<String, Entry> byName = new HashMap<>();
    private static final Map<UUID, String> namesById = new HashMap<>();

    private CoreIndex() {
    }

    /** Отмечает Ядро загруженным по указанным координатам. */
    public static synchronized void put(String name, UUID coreId, ResourceKey<Level> dimension, BlockPos pos) {
        // Ядро могло переехать: старую запись под прежним именем убираем.
        String previousName = namesById.put(coreId, name);
        if (previousName != null && !previousName.equals(name)) {
            byName.remove(previousName);
        }
        byName.put(name, new Entry(coreId, dimension, pos.immutable()));
    }

    public static synchronized void remove(UUID coreId) {
        String name = namesById.remove(coreId);
        if (name != null) {
            byName.remove(name);
        }
    }

    public static synchronized boolean isNameTaken(String name, UUID exceptCoreId) {
        Entry entry = byName.get(name);
        return entry != null && !entry.coreId().equals(exceptCoreId);
    }

    /** Ищет загруженное Ядро по имени. */
    public static synchronized Optional<CoreBlockEntity> find(MinecraftServer server, String name) {
        Entry entry = byName.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !(level.getBlockEntity(entry.pos()) instanceof CoreBlockEntity core)) {
            // Указатель разошёлся с миром — чиним на месте.
            byName.remove(name);
            namesById.remove(entry.coreId());
            return Optional.empty();
        }
        return Optional.of(core);
    }

    public static synchronized List<String> names() {
        return new ArrayList<>(byName.keySet());
    }

    /**
     * Подбирает свободное имя-заглушку вида {@code core1}. Учитывает только
     * загруженные Ядра, поэтому совпадение с выгруженным возможно; переименовать
     * его потом никто не мешает.
     */
    public static synchronized String nextFreeName() {
        for (int i = 1; ; i++) {
            String candidate = "core" + i;
            if (!byName.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    public static synchronized void reset() {
        byName.clear();
        namesById.clear();
    }
}
