package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Указатель «имя Ядра → где оно находится», переживающий перезапуск сервера.
 *
 * <p>Существует потому, что адресовать Ядро координатами неудобно: у блока на
 * физической конструкции они выглядят как 20481032, и запомнить их невозможно.
 *
 * <p>Хранится на диске: посреди боя сервер может быть перезапущен, и без
 * сохранения все имена пропали бы до того, как подгрузятся чанки с кораблями.
 *
 * <p>Записи могут разойтись с миром — Ядро переезжает при сборке конструкции,
 * его копируют схематикой, сносят сторонним модом. Поэтому поиск всегда
 * проверяет, что блок на месте, и чинит указатель, когда это не так.
 */
public final class CoreIndex extends SavedData {

    private static final String FILE_NAME = "gimpanum_cores";

    /**
     * Подстановка в образце имени: {@code tank+} задаёт все Ядра, чьё имя
     * начинается с {@code tank}.
     *
     * <p>Именно плюс, потому что Brigadier пропускает в неэкранированном слове
     * только буквы, цифры и {@code _-.+}. Звёздочка и скобки туда не проходят, а
     * подчёркивание и дефис слишком часто встречаются в самих именах.
     */
    public static final String WILDCARD = "+";

    public static final String DEFAULT_NAME_PREFIX = "core";

    private static final String KEY_CORES = "Cores";
    private static final String KEY_PREFIX = "DefaultNamePrefix";
    private static final String KEY_ID = "Id";
    private static final String KEY_NAME = "Name";
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_POS = "Pos";

    private record Entry(String name, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<UUID, Entry> byId = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    /** К нему приписываются номера при выдаче имён новым Ядрам. */
    private String defaultNamePrefix = DEFAULT_NAME_PREFIX;

    private CoreIndex() {
    }

    // --- Хранение ------------------------------------------------------------

    public static CoreIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CoreIndex::new, CoreIndex::load, null), FILE_NAME);
    }

    private static CoreIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreIndex index = new CoreIndex();
        ListTag list = tag.getList(KEY_CORES, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString(KEY_DIMENSION));
            if (dimensionId == null || !entry.hasUUID(KEY_ID)) {
                continue;
            }
            UUID coreId = entry.getUUID(KEY_ID);
            String name = entry.getString(KEY_NAME);
            int[] pos = entry.getIntArray(KEY_POS);
            if (name.isEmpty() || pos.length != 3) {
                continue;
            }
            index.byId.put(coreId, new Entry(name,
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId),
                    new BlockPos(pos[0], pos[1], pos[2])));
            index.byName.put(name, coreId);
        }
        if (tag.contains(KEY_PREFIX)) {
            index.defaultNamePrefix = tag.getString(KEY_PREFIX);
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        byId.forEach((coreId, entry) -> {
            CompoundTag stored = new CompoundTag();
            stored.putUUID(KEY_ID, coreId);
            stored.putString(KEY_NAME, entry.name());
            stored.putString(KEY_DIMENSION, entry.dimension().location().toString());
            stored.putIntArray(KEY_POS,
                    new int[]{entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()});
            list.add(stored);
        });
        tag.put(KEY_CORES, list);
        tag.putString(KEY_PREFIX, defaultNamePrefix);
        return tag;
    }

    // --- Изменение -----------------------------------------------------------

    /** Отмечает, где Ядро находится сейчас. */
    public static void put(MinecraftServer server, String name, UUID coreId,
                           ResourceKey<Level> dimension, BlockPos pos) {
        CoreIndex index = get(server);
        Entry previous = index.byId.get(coreId);
        if (previous != null && !previous.name().equals(name)) {
            index.byName.remove(previous.name());
        }
        index.byId.put(coreId, new Entry(name, dimension, pos.immutable()));
        index.byName.put(name, coreId);
        index.setDirty();
    }

    public static void remove(MinecraftServer server, UUID coreId) {
        CoreIndex index = get(server);
        Entry removed = index.byId.remove(coreId);
        if (removed != null) {
            index.byName.remove(removed.name());
            index.setDirty();
        }
    }

    public static boolean isNameTaken(MinecraftServer server, String name, UUID exceptCoreId) {
        UUID owner = get(server).byName.get(name);
        return owner != null && !owner.equals(exceptCoreId);
    }

    // --- Поиск ---------------------------------------------------------------

    /**
     * Ищет Ядро по имени.
     *
     * @param allowChunkLoad подгружать ли чанк, если Ядро выгружено. Для команд
     *                       это нужно, а вот для подсказок недопустимо: они
     *                       строятся на каждое нажатие клавиши, и подгрузка
     *                       чанков там уронила бы сервер.
     */
    public static Optional<CoreBlockEntity> find(MinecraftServer server, String name,
                                                 boolean allowChunkLoad) {
        CoreIndex index = get(server);
        UUID coreId = index.byName.get(name);
        if (coreId == null) {
            return Optional.empty();
        }
        Entry entry = index.byId.get(coreId);
        if (entry == null) {
            return Optional.empty();
        }

        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            return Optional.empty();
        }
        if (!allowChunkLoad && !level.hasChunkAt(entry.pos())) {
            return Optional.empty();
        }

        if (level.getBlockEntity(entry.pos()) instanceof CoreBlockEntity core
                && core.coreId().equals(coreId)) {
            return Optional.of(core);
        }

        // Ядро уехало или исчезло помимо нас — запись устарела.
        Gimpanum.LOGGER.debug("Указатель: запись '{}' устарела, удалена", name);
        index.byId.remove(coreId);
        index.byName.remove(name);
        index.setDirty();
        return Optional.empty();
    }

    /** Все известные имена, включая выгруженные Ядра. */
    public static List<String> names(MinecraftServer server) {
        return new ArrayList<>(get(server).byName.keySet());
    }

    /** Где записано Ядро: измерение и координаты, как есть в указателе. */
    public static Optional<String> describe(MinecraftServer server, String name) {
        CoreIndex index = get(server);
        UUID coreId = index.byName.get(name);
        if (coreId == null) {
            return Optional.empty();
        }
        Entry entry = index.byId.get(coreId);
        if (entry == null) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(entry.dimension());
        boolean loaded = level != null && level.hasChunkAt(entry.pos());
        return Optional.of(entry.dimension().location() + " " + entry.pos().toShortString()
                + (loaded ? "" : " (выгружено)"));
    }

    /**
     * Подбирает свободное имя вида {@code core1}, где {@code core} —
     * настраиваемая приставка. Учитывает и выгруженные Ядра, поэтому имена не
     * сталкиваются.
     */
    public static String nextFreeName(MinecraftServer server) {
        CoreIndex index = get(server);
        for (int i = 1; ; i++) {
            String candidate = index.defaultNamePrefix + i;
            if (!index.byName.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    public static String defaultNamePrefix(MinecraftServer server) {
        return get(server).defaultNamePrefix;
    }

    public static void setDefaultNamePrefix(MinecraftServer server, String prefix) {
        CoreIndex index = get(server);
        index.defaultNamePrefix = prefix;
        index.setDirty();
    }

    /**
     * Все Ядра, чьи имена подходят под образец с подстановкой {@link #WILDCARD}.
     *
     * <p>Остальной текст образца экранируется, поэтому точки и дефисы в именах
     * значат сами себя и лишних совпадений не дают.
     */
    public static List<CoreBlockEntity> findMatching(MinecraftServer server, String selector,
                                                     boolean allowChunkLoad) {
        Pattern pattern = toPattern(selector);
        List<CoreBlockEntity> found = new ArrayList<>();
        for (String name : names(server)) {
            if (pattern.matcher(name).matches()) {
                find(server, name, allowChunkLoad).ifPresent(found::add);
            }
        }
        return found;
    }

    /** Содержит ли строка подстановку, то есть задаёт ли она сразу много Ядер. */
    public static boolean isPattern(String selector) {
        return selector.contains(WILDCARD);
    }

    private static Pattern toPattern(String selector) {
        StringBuilder regex = new StringBuilder();
        int from = 0;
        while (true) {
            int at = selector.indexOf(WILDCARD, from);
            if (at < 0) {
                appendLiteral(regex, selector.substring(from));
                break;
            }
            appendLiteral(regex, selector.substring(from, at));
            regex.append(".*");
            from = at + WILDCARD.length();
        }
        return Pattern.compile(regex.toString());
    }

    private static void appendLiteral(StringBuilder regex, String literal) {
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal));
        }
    }
}
