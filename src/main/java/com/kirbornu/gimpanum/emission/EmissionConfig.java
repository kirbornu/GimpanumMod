package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;

import java.nio.file.Path;

/** Настройка выбросов — {@code config/gimpanum/emissions.json}, см. образец в джарке. */
public final class EmissionConfig {

    private static final JsonConfig CONFIG =
            new JsonConfig("emissions.json", "/data/gimpanum/emission/default_emissions.json");

    private EmissionConfig() {
    }

    public static JsonConfig.Section of(String name) {
        return CONFIG.section(name);
    }

    public static Path path() {
        return CONFIG.path();
    }

    public static int count() {
        return CONFIG.sections().size();
    }

    public static void load() {
        CONFIG.load();
    }
}
