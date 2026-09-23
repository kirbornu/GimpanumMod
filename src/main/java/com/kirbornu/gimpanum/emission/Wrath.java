package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Приступ Злости: все горят, и потушить нельзя.
 *
 * <p>Огонь поддерживается каждый тик, поэтому вода и снег его не гасят. Урон
 * свой, а не ванильного горения: то бьёт раз в секунду и за три минуты
 * убило бы кого угодно, а здесь реже — см. {@code damage_interval_ticks}.
 * Ванильный урон от горения на это время снимается ({@link Emissions}).
 *
 * <p>Урон — «в огне», из огненных: от него спасает огнестойкость, и это и есть
 * способ пережить приступ.
 */
final class Wrath extends LastingEmission {

    /** Сколько тиков огня держать на игроке с запасом — лишь бы не погас до следующего тика. */
    private static final int KEEP_BURNING = 20;

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("wrath").between("duration_seconds", level.random) * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("wrath");
        boolean strike = elapsed % Math.max(1, config.integer("damage_interval_ticks")) == 0;
        for (ServerPlayer player : targets) {
            player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), KEEP_BURNING));
            if (strike) {
                player.hurt(player.damageSources().inFire(), (float) config.number("damage"));
            }
        }
    }

    @Override
    public void stop(ServerLevel level) {
        level.players().forEach(ServerPlayer::clearFire);
    }
}
