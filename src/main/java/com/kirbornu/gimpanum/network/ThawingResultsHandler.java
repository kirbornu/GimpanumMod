package com.kirbornu.gimpanum.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Приём списка находок на клиенте.
 *
 * <p>Клиентская сторона спрятана за проверкой окружения и отдельным вызовом:
 * выделенный сервер тоже загружает обработчик пакета.
 */
public final class ThawingResultsHandler {

    private ThawingResultsHandler() {
    }

    public static void handle(ThawingResultsPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() ->
                com.kirbornu.gimpanum.client.ThawedOrganicsClient.accept(payload.finds()));
    }
}
