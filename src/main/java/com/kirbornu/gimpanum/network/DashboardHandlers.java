package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.dashboard.CoreDashboard;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Приём пакетов консоли.
 *
 * <p>Клиентская половина спрятана за проверкой стороны и за отдельным вызовом:
 * выделенный сервер тоже загружает этот класс, и прямая ссылка на экран уронила
 * бы его при загрузке.
 */
public final class DashboardHandlers {

    private DashboardHandlers() {
    }

    public static void open(DashboardOpenPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.kirbornu.gimpanum.client.dashboard.CoreDashboardScreen
                .accept(payload.rows(), payload.mayEditCommands()));
    }

    public static void action(DashboardActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CoreDashboard.act(player, payload.cores(),
                    payload.action(), payload.argument()));
        }
    }

    public static void apply(DashboardApplyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CoreDashboard.apply(player, payload.cores(),
                    payload.source(), payload.sections()));
        }
    }

    public static void refresh(DashboardRefreshPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> CoreDashboard.send(player));
        }
    }
}
