package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.MagicContent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Перезарядка магии — у игрока, а не у таблички.
 *
 * <p>Источник истины — вложение на игроке: момент игрового времени, до
 * которого он не может колдовать ничем. Табличка тут лишь один из способов
 * колдовать, потом появятся посохи, и они обязаны видеть ту же перезарядку.
 * Ванильная перезарядка предмета ставится только для вида — чтобы на всех
 * табличках в инвентаре была видна полоска.
 */
public final class SpellCooldown {

    private SpellCooldown() {
    }

    public static boolean ready(ServerPlayer player) {
        return player.level().getGameTime() >= player.getData(MagicContent.COOLDOWN);
    }

    public static void start(ServerPlayer player, double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        player.setData(MagicContent.COOLDOWN, player.level().getGameTime() + ticks);
        if (ticks > 0) {
            player.getCooldowns().addCooldown(MagicContent.TABLET.get(), ticks);
        }
    }
}
