package com.kirbornu.gimpanum.dashboard;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ядра, защищённые от изменений через консоль.
 *
 * <p>Смысл замка — не в правах, а в невнимательности. В консоли отмечают
 * десяток Ядер и применяют к ним действие одним нажатием; замок отмечает те,
 * которых это касаться не должно, — боевое Ядро посреди осады, эталон, с
 * которого копируют настройку. Пока замок стоит, любое изменение такого Ядра
 * отклоняется, и снять замок можно только отдельным действием.
 *
 * <p>Хранится отдельно от указателя, а не полем в настройке, намеренно:
 * настройка ездит в предмете при копировании Ядра средней кнопкой, и замок
 * поехал бы вместе с ней. Защита от случайной правки не должна
 * распространяться копированием.
 */
public final class CoreLocks extends SavedData {

    private static final String FILE_NAME = "gimpanum_core_locks";
    private static final String KEY_LOCKED = "Locked";

    private final Set<UUID> locked = new HashSet<>();

    private CoreLocks() {
    }

    public static CoreLocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CoreLocks::new, CoreLocks::load, null), FILE_NAME);
    }

    private static CoreLocks load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreLocks data = new CoreLocks();
        ListTag list = tag.getList(KEY_LOCKED, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            data.locked.add(NbtUtils.loadUUID(list.get(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        locked.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put(KEY_LOCKED, list);
        return tag;
    }

    public static boolean isLocked(MinecraftServer server, UUID coreId) {
        return get(server).locked.contains(coreId);
    }

    public static void setLocked(MinecraftServer server, UUID coreId, boolean value) {
        CoreLocks data = get(server);
        boolean changed = value ? data.locked.add(coreId) : data.locked.remove(coreId);
        if (changed) {
            data.setDirty();
        }
    }

    /** Убирает замок исчезнувшего Ядра, чтобы список не рос вечно. */
    public static void forget(MinecraftServer server, UUID coreId) {
        setLocked(server, coreId, false);
    }
}
