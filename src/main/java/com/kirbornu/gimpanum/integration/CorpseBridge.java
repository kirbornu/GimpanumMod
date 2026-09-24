package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
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
 * сколько там осталось. Поэтому вещи забираются <b>только</b> из сущности, а
 * файл служит лишь для того, чтобы её найти. Файл — снимок на миг смерти:
 * выдай мы вещи по нему, а труп остался бы стоять в мире (он просто не
 * загружен или его уже обобрали), — вышла бы вторая копия всех вещей.
 *
 * <p>Труп в незагруженном чанке сразу не найти: блоки чанк отдаёт тут же, а
 * сущности грузятся с диска отдельно и позже. Поэтому в этом случае чанк
 * подгружается на время, а игрок получает просьбу повторить.
 */
public final class CorpseBridge {

    /**
     * Чем кончилась попытка.
     *
     * @param loot    вещи из трупа; пусто — забирать нечего
     * @param pending труп, возможно, есть, но его чанк только начал грузиться
     */
    public record Reclaim(List<ItemStack> loot, boolean pending) {
        static final Reclaim NOTHING = new Reclaim(List.of(), false);
        static final Reclaim PENDING = new Reclaim(List.of(), true);
    }

    /** Сколько чанков вокруг трупа держать загруженными, пока игрок повторит попытку. */
    private static final int TICKET_RADIUS = 1;

    private static final String MOD_ID = "corpse";
    private static final String DEATH_MANAGER = "de.maxhenkel.corpse.corelib.death.DeathManager";
    private static final String DEATH = "de.maxhenkel.corpse.corelib.death.Death";
    private static final String CORPSE_ENTITY = "de.maxhenkel.corpse.entities.CorpseEntity";

    /** В каком радиусе от записанного места искать сам труп. */
    private static final double SEARCH_RADIUS = 8.0;

    /** В каком радиусе от самого игрока искать труп, когда записи нет. */
    private static final double NEARBY_RADIUS = 32.0;

    private static boolean resolved;
    private static boolean working;

    private static Method getDeaths;
    private static Method removeDeath;
    private static Method getTimestamp;
    private static Method getAllItems;
    private static Method getId;
    private static Method getDimension;
    private static Method getBlockPos;
    private static Method getPlayerUUID;
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
     * Во всех трёх случаях Талисман тратить не за что. {@code pending} —
     * труп лежит в незагруженном чанке: чанк начал грузиться, и следующая
     * попытка его найдёт.
     */
    public static Reclaim reclaim(ServerPlayer player) {
        if (!available()) {
            return Reclaim.NOTHING;
        }
        try {
            Object death = newest(player);
            ServerLevel level = death == null ? null : levelOf(player.server, death);
            Entity corpse = null;
            boolean pending = false;
            if (level != null) {
                BlockPos pos = (BlockPos) getBlockPos.invoke(death);
                if (level.hasChunkAt(pos)) {
                    corpse = corpseFor(level, pos, death);
                } else {
                    // Талисман обязан работать на любом расстоянии. Но сущности
                    // чанка грузятся позже его блоков, так что сейчас трупа не
                    // увидеть; держим чанк загруженным, пока игрок повторит.
                    level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), TICKET_RADIUS, pos);
                    pending = true;
                }
            }

            // Запись в папке мира — не единственный путь к трупу. Если она до
            // нас не дошла (её могли отключить, потерять, обрезать по
            // возрасту), ищем по округе.
            if (corpse == null) {
                corpse = corpseNear(player);
            }
            if (corpse == null) {
                Gimpanum.LOGGER.debug("Талисман: у {} нет трупа — ни по записи, ни поблизости{}",
                        player.getGameProfile().getName(), pending ? " (чанк трупа грузится)" : "");
                return pending ? Reclaim.PENDING : Reclaim.NOTHING;
            }

            Object source = getDeath(corpse);
            List<ItemStack> loot = source == null ? List.of() : items(source);
            if (loot.isEmpty()) {
                Gimpanum.LOGGER.debug("Талисман: труп {} найден, но пуст", player.getGameProfile().getName());
                return Reclaim.NOTHING;
            }
            corpse.discard();
            // Снимаем запись именно этого трупа, а не самую свежую: найденный по
            // округе труп может быть и от прежней смерти.
            if (corpse.level() instanceof ServerLevel corpseLevel) {
                removeDeath.invoke(null, corpseLevel, source);
            }
            return new Reclaim(loot, false);
        } catch (Throwable failure) {
            Gimpanum.LOGGER.error("Талисман не сумел добраться до трупа через мод Corpse", failure);
            return Reclaim.NOTHING;
        }
    }

    /**
     * Ближайший к игроку труп, который принадлежит ему же.
     *
     * <p>Запасной путь на случай, когда запись о смерти до нас не дошла.
     */
    private static Entity corpseNear(ServerPlayer player) throws Exception {
        AABB box = player.getBoundingBox().inflate(NEARBY_RADIUS);
        Entity best = null;
        double nearest = Double.MAX_VALUE;
        for (Entity entity : player.serverLevel().getEntities((Entity) null, box, corpseEntity::isInstance)) {
            Object death = getDeath(entity);
            if (death == null || !player.getUUID().equals(getPlayerUUID.invoke(death))) {
                continue;
            }
            double distance = entity.distanceToSqr(player);
            if (distance < nearest) {
                nearest = distance;
                best = entity;
            }
        }
        return best;
    }

    /** Записи о смертях игрока, как их ведёт сам Corpse. */
    private static List<?> deaths(ServerPlayer player) throws Exception {
        Object result = getDeaths.invoke(null, player.serverLevel(), player.getUUID());
        return result instanceof List<?> list ? list : List.of();
    }

    /** Самая свежая запись о смерти этого игрока. */
    private static Object newest(ServerPlayer player) throws Exception {
        List<?> deaths = deaths(player);
        if (deaths.isEmpty()) {
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
     * Сама сущность трупа рядом с записанным местом. Чанк к этому моменту
     * уже загружен — см. {@link #reclaim}.
     */
    private static Entity corpseFor(ServerLevel level, BlockPos pos, Object death) throws Exception {
        UUID id = (UUID) getId.invoke(death);
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

            // Берём перегрузку по уровню и UUID, а не по игроку: та, что
            // принимает игрока, в Corpse 1.1.13 вызывает саму себя и валится
            // переполнением стека. Мод её и не использует, поэтому никто до
            // сих пор и не заметил. Папка смертей всё равно одна на весь мир,
            // а не на измерение, — так что уровень подойдёт любой.
            getDeaths = manager.getMethod("getDeaths", ServerLevel.class, UUID.class);
            removeDeath = manager.getMethod("removeDeath", ServerLevel.class, death);
            getTimestamp = death.getMethod("getTimestamp");
            getAllItems = death.getMethod("getAllItems");
            getId = death.getMethod("getId");
            getDimension = death.getMethod("getDimension");
            getBlockPos = death.getMethod("getBlockPos");
            getPlayerUUID = death.getMethod("getPlayerUUID");
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
