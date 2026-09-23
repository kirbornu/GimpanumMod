package com.kirbornu.gimpanum.lore;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Откуда лор попадает к игрокам.
 *
 * <p>Из некрофагов — редко, со своим шансом у каждого: это часть их добычи и
 * живёт вместе с ней, в {@link com.kirbornu.gimpanum.entity.MobLoot}. Здесь —
 * только чтение папки с книгами.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class LoreEvents {

    private LoreEvents() {
    }

    /** Папка читается на старте сервера, рядом с настройками конвертеров. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LoreBooks.load();
    }
}
