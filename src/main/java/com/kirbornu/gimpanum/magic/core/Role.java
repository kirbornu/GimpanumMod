package com.kirbornu.gimpanum.magic.core;

import java.util.Locale;

/** Роль аркана в заклинании. Определяется грамматикой, а не самим арканом. */
public enum Role {
    ROOT,
    PREFIX,
    SUFFIX,
    INTERFIX;

    /** Имя роли в {@code arcanes.json}: {@code root}, {@code prefix}, {@code suffix}, {@code interfix}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
