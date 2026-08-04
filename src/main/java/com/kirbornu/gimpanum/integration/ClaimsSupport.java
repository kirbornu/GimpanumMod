package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Точка входа к клеймам территорий.
 *
 * <p>Клеймовых модов в сборке несколько, но публичный API есть только у Open
 * Parties and Claims — с ним и работаем. Мод необязателен, поэтому здесь нет ни
 * одного его типа: всё уходит в {@link ClaimsBridge}, который загружается лишь
 * после успешной проверки {@link #isAvailable()}.
 *
 * <p>Без OPAC Контрольная точка работает полностью, только чанк вокруг неё не
 * клеймится.
 */
public final class ClaimsSupport {

    private static final String OPAC_MOD_ID = "openpartiesandclaims";

    private static Boolean modLoaded;
    private static boolean bridgeFailed;

    private ClaimsSupport() {
    }

    public static boolean isAvailable(MinecraftServer server) {
        if (modLoaded == null) {
            modLoaded = ModList.get().isLoaded(OPAC_MOD_ID);
            Gimpanum.LOGGER.info("Open Parties and Claims {} — клеймы Контрольных точек {}",
                    modLoaded ? "обнаружен" : "не найден",
                    modLoaded ? "включены" : "отключены");
        }
        if (!modLoaded || bridgeFailed) {
            return false;
        }
        try {
            return ClaimsBridge.isReady(server);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /**
     * Отдаёт чанк точки указанному владельцу, вытесняя прежний клейм.
     *
     * @return {@code true}, если клейм действительно поставлен
     */
    public static boolean claim(ServerLevel level, BlockPos pos, UUID owner) {
        if (!isAvailable(level.getServer())) {
            return false;
        }
        try {
            return ClaimsBridge.claim(level, pos, owner);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public static void unclaim(ServerLevel level, BlockPos pos) {
        if (!isAvailable(level.getServer())) {
            return;
        }
        try {
            ClaimsBridge.unclaim(level, pos);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Кому чанк принадлежит сейчас; {@code null}, если ничей или OPAC нет. */
    public static UUID ownerAt(ServerLevel level, BlockPos pos) {
        if (!isAvailable(level.getServer())) {
            return null;
        }
        try {
            return ClaimsBridge.ownerAt(level, pos);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /** Цвет клеймов игрока на карте — им же красятся частицы точки. */
    public static OptionalInt claimColor(MinecraftServer server, UUID owner) {
        if (!isAvailable(server)) {
            return OptionalInt.empty();
        }
        try {
            return ClaimsBridge.claimColor(server, owner);
        } catch (Throwable t) {
            fail(t);
            return OptionalInt.empty();
        }
    }

    /**
     * Название и цвет клеймов игрока.
     *
     * <p>Годится только для фиктивного игрока: у живого это затёрло бы его
     * собственные настройки на всей карте.
     */
    public static void setAppearance(MinecraftServer server, UUID owner, String name, int color) {
        if (!isAvailable(server)) {
            return;
        }
        try {
            ClaimsBridge.setAppearance(server, owner, name, color);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void fail(Throwable t) {
        // Ломать игру из-за несовместимости с чужим модом нельзя.
        bridgeFailed = true;
        Gimpanum.LOGGER.error("Мост к Open Parties and Claims отключён после ошибки; "
                + "клеймы Контрольных точек выключены до перезапуска", t);
    }
}
