package com.kirbornu.gimpanum.magic.core;

/**
 * Роли арканов по их порядковым номерам.
 *
 * <p>Каждый аркан сравнивает свой номер с соседями, а по краям заклинания
 * стоят нули. Номера положительны, поэтому крайние арканы всегда «выше» края:
 * первый не бывает ни суффиксом, ни интерфиксом, последний — ни префиксом, ни
 * интерфиксом. Отсюда и гарантии, которые проверяют тесты: корень есть всегда,
 * у префикса есть корень справа, у суффикса — слева.
 *
 * <pre>
 *            R &lt;      R =        R &gt;
 *   L &lt;    корень    корень     префикс
 *   L =    корень    интерфикс  префикс
 *   L &gt;    суффикс   суффикс    интерфикс
 * </pre>
 */
public final class Grammar {

    private static final Role[][] TABLE = {
            // R <          R =            R >
            {Role.ROOT, Role.ROOT, Role.PREFIX},         // L <
            {Role.ROOT, Role.INTERFIX, Role.PREFIX},     // L =
            {Role.SUFFIX, Role.SUFFIX, Role.INTERFIX},   // L >
    };

    private Grammar() {
    }

    public static Role[] roles(int[] orders) {
        Role[] roles = new Role[orders.length];
        for (int i = 0; i < orders.length; i++) {
            int left = i > 0 ? orders[i - 1] : 0;
            int right = i < orders.length - 1 ? orders[i + 1] : 0;
            roles[i] = TABLE[compare(left, orders[i])][compare(right, orders[i])];
        }
        return roles;
    }

    /** 0 — сосед меньше, 1 — равен, 2 — больше. */
    private static int compare(int neighbour, int self) {
        return neighbour < self ? 0 : neighbour == self ? 1 : 2;
    }

    /**
     * Какие арканы схлопнуты.
     *
     * <p>Из нескольких корней подряд работает только первый, из нескольких
     * интерфиксов подряд — тоже. Других схлопываний нет: два префикса подряд —
     * это два префикса.
     */
    public static boolean[] collapsed(Role[] roles) {
        boolean[] collapsed = new boolean[roles.length];
        for (int i = 1; i < roles.length; i++) {
            collapsed[i] = roles[i] == roles[i - 1] && (roles[i] == Role.ROOT || roles[i] == Role.INTERFIX);
        }
        return collapsed;
    }
}
