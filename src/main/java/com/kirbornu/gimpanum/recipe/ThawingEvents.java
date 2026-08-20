package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.kirbornu.gimpanum.network.GimpanumNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    /**
     * Вошедший получает список находок сразу.
     *
     * <p>Он нужен просмотрщику рецептов, а тот открывается когда угодно;
     * ждать ближайшей правки конфига нельзя — её может не случиться никогда.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GimpanumNetwork.sendThawingResults(player);
        }
    }
}
