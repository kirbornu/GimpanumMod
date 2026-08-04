package com.kirbornu.gimpanum.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Единственный класс мода, который напрямую ссылается на типы Open Parties and
 * Claims.
 *
 * <p>Изоляция такая же, как у мостов к Sable и FTB Teams: JVM загружает класс
 * лениво, поэтому пока {@link ClaimsSupport} не вызовет отсюда метод,
 * отсутствие OPAC ничем не грозит. Не обращаться к этому классу, минуя
 * {@link ClaimsSupport}.
 *
 * <p>Используются низкоуровневые {@code claim}/{@code unclaim}, а не
 * {@code tryToClaim}: последние проверяют лимиты чанков и права игрока, а
 * Контрольная точка ставится оператором и подчиняться лимитам не должна.
 */
final class ClaimsBridge {

    private ClaimsBridge() {
    }

    static boolean isReady(MinecraftServer server) {
        return OpenPACServerAPI.get(server) != null;
    }

    /**
     * Переводит чанк блока во владение указанного игрока, вытесняя прежний
     * клейм.
     *
     * @return {@code false}, если в этом измерении клеймы запрещены настройкой
     *         сервера
     */
    static boolean claim(ServerLevel level, BlockPos pos, UUID owner) {
        MinecraftServer server = level.getServer();
        ResourceLocation dimension = level.dimension().location();
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (!claims.isClaimable(dimension)) {
            return false;
        }
        // replace=true: захват точки обязан отобрать чанк у прежнего владельца,
        // иначе первый же захват намертво закрепил бы точку за одной командой.
        claims.claim(dimension, owner, subConfigIndex(server, owner),
                pos.getX() >> 4, pos.getZ() >> 4, true);
        return true;
    }

    static void unclaim(ServerLevel level, BlockPos pos) {
        OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
                .unclaim(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Владелец чанка по версии OPAC — чтобы не переклеймливать уже своё. */
    static UUID ownerAt(ServerLevel level, BlockPos pos) {
        var claim = OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
                .get(level.dimension().location(), pos);
        return claim == null ? null : claim.getPlayerId();
    }

    /** Цвет, которым клеймы этого игрока рисуются на карте. */
    static OptionalInt claimColor(MinecraftServer server, UUID owner) {
        IPlayerConfigAPI config = config(server, owner);
        if (config == null) {
            return OptionalInt.empty();
        }
        Integer color = config.getEffective(PlayerConfigOptions.CLAIMS_COLOR);
        return color == null ? OptionalInt.empty() : OptionalInt.of(color);
    }

    /**
     * Задаёт вид клеймов игрока.
     *
     * <p>Вызывать можно только для фиктивного игрока: у живого это затёрло бы
     * его собственное название и цвет клеймов на всей карте.
     */
    static void setAppearance(MinecraftServer server, UUID owner, String name, int color) {
        IPlayerConfigAPI config = config(server, owner);
        if (config == null) {
            return;
        }
        config.tryToSet(PlayerConfigOptions.CLAIMS_NAME, name);
        config.tryToSet(PlayerConfigOptions.CLAIMS_COLOR, color);
    }

    private static IPlayerConfigAPI config(MinecraftServer server, UUID owner) {
        return OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(owner);
    }

    /**
     * Подконфигурация, в которой игрок сейчас клеймит.
     *
     * <p>У основной подконфигурации индекс {@code -1}; он же и запасной вариант,
     * если настройку прочитать не удалось.
     */
    private static int subConfigIndex(MinecraftServer server, UUID owner) {
        IPlayerConfigAPI config = config(server, owner);
        if (config == null) {
            return -1;
        }
        IPlayerConfigAPI used = config.getUsedSubConfig();
        return used == null ? -1 : used.getSubIndex();
    }
}
