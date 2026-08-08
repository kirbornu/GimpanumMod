package com.kirbornu.gimpanum.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Приём списка конвертеров на клиенте.
 *
 * <p>Обращение к клиентскому коду спрятано за проверкой стороны и отдельным
 * классом: выделенный сервер тоже загружает обработчик пакета, и прямая ссылка
 * на клиентские классы уронила бы его при загрузке.
 */
public final class ConverterMarkersHandler {

    private ConverterMarkersHandler() {
    }

    public static void handle(ConverterMarkersPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.kirbornu.gimpanum.client.ConverterMarkers.accept(payload.markers()));
    }
}
