package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.config.MagicSettings;
import net.minecraft.server.level.ServerPlayer;

/**
 * Чем платят за заклинание.
 *
 * <p>Точка расширения: сейчас платят едой, потом будут платить эрозией и
 * хрусталём. Новый способ — новая реализация, подставленная в
 * {@link #current}; табличка и посохи об этом знать не будут.
 */
public interface SpellPayment {

    /**
     * Списать цену, если игрок может её заплатить.
     *
     * @param runes сколько рун в заклинании
     * @return {@code true} — заплачено; {@code false} — не списано ничего
     */
    boolean tryPay(ServerPlayer player, int runes, MagicSettings settings);

    /** Действующий способ оплаты. */
    static SpellPayment current() {
        return Holder.current;
    }

    static void use(SpellPayment payment) {
        Holder.current = payment;
    }

    /** Хранилище для {@link #current()}: у интерфейса не бывает изменяемых полей. */
    final class Holder {
        private static SpellPayment current = new FoodPayment();

        private Holder() {
        }
    }
}
