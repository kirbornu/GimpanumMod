package com.kirbornu.gimpanum.dashboard;

import com.kirbornu.gimpanum.core.CoreConfig;

/**
 * Часть настройки Ядра, которую можно перенести отдельно от остальных.
 *
 * <p>Существует ради переноса настройки с одного Ядра на другие: копировать
 * целиком почти всегда неверно — имя обязано быть уникальным, привязанные
 * игроки у каждого Ядра свои, а вот условия выдачи и мощность взрыва обычно
 * нужны одинаковые на всю линию обороны. Поэтому копируется набор частей, а не
 * настройка.
 *
 * <p>Тот же набор служит и обычной правке одного Ядра: там просто включены все
 * части, кроме тех, что консоль не показывала.
 */
public enum ConfigSection {

    NAME,
    FLAGS,
    PLAYERS,
    TEAMS,
    COMMANDS,
    SEAL,
    SPAWN,
    EXPLOSION;

    private static final ConfigSection[] VALUES = values();

    /** Все части разом — маска для правки одного Ядра целиком. */
    public static final int ALL = (1 << VALUES.length) - 1;

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }

    /**
     * Какие части у двух настроек различаются.
     *
     * <p>Считается через {@link #merge}, а не отдельным перечислением полей:
     * два списка полей неизбежно разошлись бы при добавлении следующей
     * настройки, и различие молча перестало бы замечаться. Здесь же «часть
     * различается» определено как «перенос этой части что-то меняет» — то же
     * самое определение, которым перенос и пользуется.
     */
    public static int changed(CoreConfig base, CoreConfig edited) {
        int mask = 0;
        for (ConfigSection section : VALUES) {
            if (!merge(base, edited, section.bit()).equals(base)) {
                mask |= section.bit();
            }
        }
        return mask;
    }

    /**
     * Переносит выбранные части из образца в цель.
     *
     * <p>Порядок внутри {@link #FLAGS} задан замыслом. {@code withArmed} умеет
     * заодно снимать неразрушимость, если у Ядра включено автоснятие, — и это
     * правильно для живого снятия предохранителя, но не для копирования: здесь
     * цель обязана получить ровно то, что было у образца. Поэтому сперва
     * автоснятие, потом предохранитель, и лишь затем неразрушимость, которая
     * и перекрывает возможное побочное действие.
     */
    public static CoreConfig merge(CoreConfig target, CoreConfig source, int mask) {
        CoreConfig result = target;
        if (NAME.in(mask)) {
            result = result.withName(source.name());
        }
        if (FLAGS.in(mask)) {
            result = result.withAutoDisableInvulnerable(source.autoDisableInvulnerable())
                    .withArmed(source.armed())
                    .withInvulnerable(source.invulnerable());
        }
        if (PLAYERS.in(mask)) {
            result = result.withBoundPlayers(source.boundPlayers());
        }
        if (TEAMS.in(mask)) {
            result = result.withBoundTeams(source.boundTeams());
        }
        if (COMMANDS.in(mask)) {
            result = result.withCommands(source.commands())
                    .withDeathCommands(source.deathCommands());
        }
        if (SEAL.in(mask)) {
            result = result.withSealEnabled(source.sealEnabled())
                    .withSealPrice(source.sealPrice())
                    .withSealPostfix(source.sealPostfix());
        }
        if (SPAWN.in(mask)) {
            result = result.withSpawn(source.spawn());
        }
        if (EXPLOSION.in(mask)) {
            result = result.withExplosionEnabled(source.explosionEnabled())
                    .withExplosion(source.explosionPower(), source.explosionFire());
        }
        return result;
    }
}
