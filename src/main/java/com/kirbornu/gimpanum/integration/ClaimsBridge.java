package com.kirbornu.gimpanum.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;

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
 * <p>Используется низкоуровневый {@code unclaim}, а не {@code tryToUnclaim}:
 * последний проверяет права игрока, а заявку в Гимпануме снимает не игрок, а
 * сам мод, и спрашивать разрешения ему не у кого.
 */
final class ClaimsBridge {

    private ClaimsBridge() {
    }

    static boolean isReady(MinecraftServer server) {
        return OpenPACServerAPI.get(server) != null;
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

}
