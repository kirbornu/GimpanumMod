package com.kirbornu.gimpanum.converter;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.network.GimpanumNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Рассылка меток конвертеров игрокам. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class ConverterEvents {

    private ConverterEvents() {
    }

    /**
     * Вошедший получает список сразу.
     *
     * <p>Ждать ближайшего изменения нельзя: конвертеры стоят месяцами, и до
     * первой правки игрок остался бы без меток вовсе.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GimpanumNetwork.sendMarkers(player);
        }
    }

    /** Предложения читаются на старте сервера: до него реестры ещё не готовы. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConverterOffers.load(event.getServer());
    }
}

