package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.network.MagicCooldownPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Перезарядка магии — у игрока, а не у таблички.
 *
 * <p>Источник истины — вложение на игроке: момент игрового времени, до
 * которого он не может колдовать ничем. Табличка тут лишь один из способов
 * колдовать, потом появятся посохи, и они обязаны видеть ту же перезарядку.
 *
 * <p>Ванильная перезарядка предмета здесь нарочно не ставится: она глушит
 * любое использование предмета, и табличку нельзя было бы даже открыть, пока
 * магия перезаряжается. Вместо неё клиенту уходит пакет, и полоску на
 * табличках рисует {@link CooldownView}. Колдовать раньше срока это не даёт:
 * каст всё равно упирается в {@link #ready} на сервере.
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
            PacketDistributor.sendToPlayer(player, new MagicCooldownPayload(ticks));
        }
    }
}
