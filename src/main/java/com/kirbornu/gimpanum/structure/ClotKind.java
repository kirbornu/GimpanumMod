package com.kirbornu.gimpanum.structure;

import net.minecraft.util.StringRepresentable;

/**
 * Чья это награда — какой структуре принадлежит сгусток и какую ловушку он
 * спускает. Имя — оно же раздел в {@code structures.json}.
 */
public enum ClotKind implements StringRepresentable {
    FROZEN_MEMORY("frozen_memory"),
    GLASS_DREAMS("glass_dreams"),
    RADITA_GALLERY("radita_gallery"),
    PRIMO_CITY("primo_city");

    private final String id;

    ClotKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
