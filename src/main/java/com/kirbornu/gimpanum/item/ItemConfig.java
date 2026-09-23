package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.config.JsonConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.nio.file.Path;

/** Настройка предметов высшего тира — {@code config/gimpanum/items.json}, см. образец в джарке. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class ItemConfig {

    private static final JsonConfig CONFIG =
            new JsonConfig("items.json", "/data/gimpanum/item_config/default_items.json");

    private ItemConfig() {
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
