package com.kirbornu.gimpanum.structure;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.config.JsonConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.nio.file.Path;

/** Настройка структур Гимпанума — {@code config/gimpanum/structures.json}, см. образец в джарке. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class StructureConfig {

    private static final JsonConfig CONFIG =
            new JsonConfig("structures.json", "/data/gimpanum/structure_traps/default_structures.json");

    private StructureConfig() {
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

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load();
    }
}
