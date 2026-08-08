package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.network.ConverterMarkersPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Метки конвертеров на карте игрока.
 *
 * <p>Вейпоинты у Xaero целиком клиентские — сервер их не видит и разослать не
 * может, поэтому список конвертеров приезжает своим пакетом, а раскладывает его
 * по миникарте уже клиент.
 *
 * <p>Набор перекладывается заново раз в пару секунд, а не по событию. Так проще
 * и надёжнее: смена измерения, пересоздание сессии Xaero и переподключение
 * обрабатываются сами собой, а перекладка идемпотентна и стоит копейки при
 * десятке обменников.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class ConverterMarkers {

    private static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";

    /** Раз в две секунды: метки не двигаются, чаще незачем. */
    private static final int APPLY_INTERVAL_TICKS = 40;

    private static List<ConverterMarkersPayload.Marker> markers = List.of();

    private static Boolean minimapLoaded;
    private static boolean bridgeFailed;
    private static int timer;

    private ConverterMarkers() {
    }

    /** Принимает новый список от сервера и просит переложить набор сразу. */
    public static void accept(List<ConverterMarkersPayload.Marker> received) {
        markers = List.copyOf(received);
        timer = APPLY_INTERVAL_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++timer < APPLY_INTERVAL_TICKS) {
            return;
        }
        timer = 0;
        apply();
    }

    private static void apply() {
        if (!isAvailable()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<ConverterMarkersPayload.Marker> here = new ArrayList<>();
        for (ConverterMarkersPayload.Marker marker : markers) {
            // Вейпоинты принадлежат измерению, поэтому чужие сюда класть нельзя.
            if (marker.dimension().equals(dimension)) {
                here.add(marker);
            }
        }

        try {
            XaeroWaypointBridge.apply(here);
        } catch (Throwable t) {
            // Ломать клиент из-за несовместимости с чужим модом нельзя.
            bridgeFailed = true;
            Gimpanum.LOGGER.error("Мост к Xaero отключён после ошибки; "
                    + "метки конвертеров выключены до перезапуска", t);
        }
    }

    private static boolean isAvailable() {
        if (minimapLoaded == null) {
            minimapLoaded = ModList.get().isLoaded(XAERO_MINIMAP_MOD_ID);
            Gimpanum.LOGGER.info("Xaero's Minimap {} — метки конвертеров {}",
                    minimapLoaded ? "обнаружен" : "не найден",
                    minimapLoaded ? "включены" : "отключены");
        }
        return minimapLoaded && !bridgeFailed;
    }
}
