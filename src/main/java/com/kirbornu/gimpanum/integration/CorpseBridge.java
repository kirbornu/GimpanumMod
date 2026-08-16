package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Разговор с модом Corpse — через рефлексию и только через неё.
 *
 * <p>Гимпанум не должен ни компилироваться против Corpse, ни падать без него:
 * это чужой мод сборки, а не наша основа. Поэтому классы ищутся по имени, а
 * методы — по найденным классам. Если Corpse нет, {@link #available()} скажет
 * «нет», и Талисман честно откажется работать.
 *
 * <p>Записей о смерти две штуки на каждую смерть, и это важно. Одна — файл в
 * папке мира, её ведёт {@code DeathManager}. Вторая — сама сущность трупа,
 * стоящая в мире; именно из неё игроки вынимают вещи, и именно она знает,
 * сколько там осталось. Поэтому забираем из сущности, если она нашлась, и
 * только если не нашлась — из файла. Иначе Талисман выдавал бы копию уже
 * разграбленного трупа.
 */
public final class CorpseBridge {

    private static final String MOD_ID = "corpse";
    private static final String DEATH_MANAGER = "de.maxhenkel.corpse.corelib.death.DeathManager";
    private static final String DEATH = "de.maxhenkel.corpse.corelib.death.Death";
    private static final String CORPSE_ENTITY = "de.maxhenkel.corpse.entities.CorpseEntity";

    /** В каком радиусе от записанного места искать сам труп. */
    private static final double SEARCH_RADIUS = 8.0;

    private static boolean resolved;
    private static boolean working;

    private static Method getDeaths;
    private static Method removeDeath;
    private static Method getTimestamp;
    private static Method getAllItems;
    private static Method getId;
    private static Method getDimension;
    private static Method getBlockPos;
    private static Class<?> corpseEntity;
    private static Method getCorpseUUID;

    private CorpseBridge() {
    }

    public static boolean available() {
        resolve();
        return working;
    }

    /**
     * Забирает всё из последнего трупа игрока и убирает труп.
     *
     * <p>Пусто — значит трупа нет, он уже разграблен или Corpse не установлен.
     * Во всех трёх случаях Талисман тратить не за что.
     */
    public static List<ItemStack> reclaim(ServerPlayer player) {
        if (!available()) {
            return List.of();
        }
        try {
            Object death = newest(player);
            if (death == null) {
                return List.of();
            }
            ServerLevel level = levelOf(player.server, death);
            Entity corpse = level == null ? null : corpseFor(level, death);

            // Живая сущность знает больше файла: из неё и берём.
            Object source = corpse != null ? getDeath(corpse) : death;
            List<ItemStack> loot = items(source);
            if (loot.isEmpty()) {
                return List.of();
            }
            if (corpse != null) {
                corpse.discard();
            }
            if (level != null) {
                removeDeath.invoke(null, level, death);
            }
            return loot;
        } catch (Throwable failure) {
            Gimpanum.LOGGER.error("Талисман не сумел добраться до трупа через мод Corpse", failure);
            return List.of();
        }
    }

    /** Самая свежая запись о смерти этого игрока. */
    private static Object newest(ServerPlayer player) throws Exception {
        Object result = getDeaths.invoke(null, player);
        if (!(result instanceof List<?> deaths) || deaths.isEmpty()) {
            return null;
        }
        Object newest = null;
        long best = Long.MIN_VALUE;
        for (Object death : deaths) {
            long stamp = (long) getTimestamp.invoke(death);
            if (stamp > best) {
                best = stamp;
                newest = death;
            }
        }
        return newest;
    }

    private static ServerLevel levelOf(MinecraftServer server, Object death) throws Exception {
        String id = (String) getDimension.invoke(death);
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }

    /**
     * Сама сущность трупа рядом с записанным местом.
     *
     * <p>Чанк подгружаем нарочно: труп может стоять там, где давно никого нет,
     * а Талисман обязан работать на любом расстоянии.
     */
    private static Entity corpseFor(ServerLevel level, Object death) throws Exception {
        BlockPos pos = (BlockPos) getBlockPos.invoke(death);
        UUID id = (UUID) getId.invoke(death);
        level.getChunk(pos);
        AABB box = new AABB(pos).inflate(SEARCH_RADIUS);
        for (Entity entity : level.getEntities((Entity) null, box, e -> corpseEntity.isInstance(e))) {
            Object found = getCorpseUUID.invoke(entity);
            if (found instanceof Optional<?> maybe && maybe.isPresent() && maybe.get().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    private static Object getDeath(Entity corpse) throws Exception {
        return corpseEntity.getMethod("getDeath").invoke(corpse);
    }

    private static List<ItemStack> items(Object death) throws Exception {
        Object all = getAllItems.invoke(death);
        List<ItemStack> loot = new ArrayList<>();
        if (all instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof ItemStack stack && !stack.isEmpty()) {
                    loot.add(stack.copy());
                }
            }
        }
        return loot;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> manager = Class.forName(DEATH_MANAGER);
            Class<?> death = Class.forName(DEATH);
            corpseEntity = Class.forName(CORPSE_ENTITY);

            getDeaths = manager.getMethod("getDeaths", ServerPlayer.class);
            removeDeath = manager.getMethod("removeDeath", ServerLevel.class, death);
            getTimestamp = death.getMethod("getTimestamp");
            getAllItems = death.getMethod("getAllItems");
            getId = death.getMethod("getId");
            getDimension = death.getMethod("getDimension");
            getBlockPos = death.getMethod("getBlockPos");
            getCorpseUUID = corpseEntity.getMethod("getCorpseUUID");
            working = true;
        } catch (Throwable failure) {
            Gimpanum.LOGGER.warn("Мод Corpse есть, но его устройство изменилось — "
                    + "Талисман лиловой королевы работать не будет", failure);
        }
    }

    /** Уровень, в котором стоит существо, — мелочь, но нужна и здесь, и там. */
    public static boolean isServer(Level level) {
        return level instanceof ServerLevel;
    }
}
