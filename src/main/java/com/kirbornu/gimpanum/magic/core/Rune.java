package com.kirbornu.gimpanum.magic.core;

import java.util.Locale;

/**
 * Двенадцать базовых рун.
 *
 * <p>Порядок объявления — это и есть индекс руны, на который ссылаются
 * настройки, тесты и песочница. Переставлять нельзя, добавлять — только в
 * конец.
 */
public enum Rune {
    AN("АН"),
    BE("БЭ"),
    VOL("ВОЛ"),
    GI("ГИ"),
    DAR("ДАР"),
    ER("ЭР"),
    ZHIN("ЖИН"),
    ZAR("ЗАР"),
    IM("ИМ"),
    KOL("КОЛ"),
    LUN("ЛУН"),
    MAR("МАР");

    private static final Rune[] BY_INDEX = values();

    private final String title;

    Rune(String title) {
        this.title = title;
    }

    /** Латинский id: часть имени глифа и ключ в {@code magic.json}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Как руну пишут люди — для сообщений и подсказок. */
    public String title() {
        return title;
    }

    public int index() {
        return ordinal();
    }

    public static int count() {
        return BY_INDEX.length;
    }

    public static Rune byIndex(int index) {
        return BY_INDEX[index];
    }

    /** @return руна с таким id или {@code null} */
    public static Rune byId(String id) {
        for (Rune rune : BY_INDEX) {
            if (rune.id().equals(id)) {
                return rune;
            }
        }
        return null;
    }
}
