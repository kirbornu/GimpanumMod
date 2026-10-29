package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.config.MagicConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Чем платят за заклинание.
 *
 * <p>Точка расширения: сейчас платят едой и хрусталём, потом добавится
 * эрозия. Новый способ — новая реализация, подставленная в
 * {@link #current}; табличка и посохи об этом знать не будут.
 */
public interface SpellPayment {

    /**
     * Списать цену, если игрок может её заплатить.
     *
     * @param runes сколько рун в заклинании
     * @return пусто — заплачено; иначе чего не хватило, и не списано ничего
     */
    Optional<SpellCaster.Failure> tryPay(ServerPlayer player, int runes, MagicConfig.Loaded config);

    /** Действующий способ оплаты. */
    static SpellPayment current() {
        return Holder.current;
    }

    static void use(SpellPayment payment) {
        Holder.current = payment;
    }

    /** Хранилище для {@link #current()}: у интерфейса не бывает изменяемых полей. */
    final class Holder {
        private static SpellPayment current = new RunePayment();

        private Holder() {
        }
    }
}
