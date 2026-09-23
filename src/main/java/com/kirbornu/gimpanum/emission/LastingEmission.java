package com.kirbornu.gimpanum.emission;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Выброс, который длится: отсчитывает своё время и каждый тик что-то делает. */
abstract class LastingEmission implements Emission {

    /** Сколько тиков осталось. */
    protected int left;

    /** Сколько тиков прошло с начала. */
    protected int elapsed;

    /** Сколько тиков выброс продлится — спрашивается один раз, при начале. */
    protected abstract int duration(ServerLevel level);

    /** Что делать каждый тик, пока выброс идёт. */
    protected abstract void pulse(ServerLevel level, List<ServerPlayer> targets);

    @Override
    public void start(ServerLevel level, List<ServerPlayer> targets) {
        left = duration(level);
    }

    @Override
    public boolean tick(ServerLevel level, List<ServerPlayer> targets) {
        if (left-- <= 0) {
            return false;
        }
        pulse(level, targets);
        elapsed++;
        return true;
    }

    @Override
    public boolean lasting() {
        return true;
    }
}
