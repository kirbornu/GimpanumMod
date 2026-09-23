package com.kirbornu.gimpanum.emission;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Один выброс, от начала до конца.
 *
 * <p>{@code targets} — те, кого выброс касается: игроки в Гимпануме, кроме
 * творческого режима и наблюдателей. Список пересобирается каждый тик, поэтому
 * вошедший посреди выброса попадает под него сразу.
 */
public interface Emission {

    void start(ServerLevel level, List<ServerPlayer> targets);

    /** Ещё идёт? Разовый выброс делает всё в {@link #start} и отвечает «нет». */
    boolean tick(ServerLevel level, List<ServerPlayer> targets);

    /** Прибрать за собой — по окончании или по команде. */
    default void stop(ServerLevel level) {
    }

    /** Длится ли выброс — тогда об окончании стоит сообщить. */
    default boolean lasting() {
        return false;
    }
}
