package com.kirbornu.gimpanum.magic.cast;

/**
 * Перезарядка магии глазами клиента — только чтобы нарисовать полоску на табличках.
 *
 * <p>Ничего не решает: можно ли колдовать, проверяет сервер по вложению на
 * игроке. Сюда сервер лишь сообщает, сколько тиков осталось, а полоска тает
 * по настоящему времени. Отстать от серверного счёта она может только на
 * подвисания сервера — для картинки это неважно.
 */
public final class CooldownView {

    private static final long MILLIS_PER_TICK = 50;

    private static volatile long endsAt;
    private static volatile long total;

    private CooldownView() {
    }

    public static void start(int ticks) {
        total = Math.max(0, ticks) * MILLIS_PER_TICK;
        endsAt = System.currentTimeMillis() + total;
    }

    /** Какая доля перезарядки осталась: 1 — только началась, 0 — закончилась. */
    public static float remaining() {
        long left = endsAt - System.currentTimeMillis();
        return left <= 0 || total <= 0 ? 0F : Math.min(1F, (float) left / total);
    }
}
