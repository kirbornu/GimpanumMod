package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Чтение содержимого Замороженной органики при запуске сервера. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class ThawingEvents {

    private ThawingEvents() {
    }

    /** Именно на старте сервера: раньше реестр предметов ещё не готов. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ThawedOrganics.load(event.getServer());
    }
}
