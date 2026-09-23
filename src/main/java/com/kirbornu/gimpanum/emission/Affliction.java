package com.kirbornu.gimpanum.emission;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * Приступ, который вешает на всех один эффект: Уныние или Зависть.
 *
 * <p>Эффект держится до конца приступа и возвращается, если его сняли: молоко
 * избавляет лишь на пару секунд. Длительность у эффекта — ровно остаток
 * приступа, так что по окончании он уходит сам.
 */
final class Affliction extends LastingEmission {

    /** Как часто проверять, не сняли ли эффект. */
    private static final int RECHECK = 20;

    private final String name;
    private final Holder<MobEffect> effect;

    private Affliction(String name, Holder<MobEffect> effect) {
        this.name = name;
        this.effect = effect;
    }

    /** Уныние — Утомление, как от древнего стража. */
    static Affliction despondency() {
        return new Affliction("despondency", MobEffects.DIG_SLOWDOWN);
    }

    /** Зависть — Отравление. Само не убивает: оставляет 1 HP, а дальше хватит любого удара. */
    static Affliction envy() {
        return new Affliction("envy", MobEffects.POISON);
    }

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of(name).integer("duration_seconds") * 20;
    }

    @Override
    public void start(ServerLevel level, List<ServerPlayer> targets) {
        super.start(level, targets);
        if (effect == MobEffects.DIG_SLOWDOWN) {
            // Лицо древнего стража на весь экран — то же, что видит ныряльщик у
            // подводного храма. По нему Уныние узнают с первой секунды.
            targets.forEach(player -> player.connection.send(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F)));
        }
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        if (elapsed % RECHECK != 0) {
            return;
        }
        int amplifier = EmissionConfig.of(name).integer("amplifier");
        for (ServerPlayer player : targets) {
            MobEffectInstance current = player.getEffect(effect);
            if (current == null || current.getAmplifier() < amplifier) {
                player.addEffect(new MobEffectInstance(effect, left, amplifier));
            }
        }
    }
}
