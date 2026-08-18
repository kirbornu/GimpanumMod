package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.converter.ConverterBlockEntity;
import com.kirbornu.gimpanum.converter.ConverterIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Пакеты мода и их рассылка. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class GimpanumNetwork {

    private GimpanumNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ConverterMarkersPayload.TYPE, ConverterMarkersPayload.STREAM_CODEC,
                ConverterMarkersHandler::handle);

        registrar.playToClient(DashboardOpenPayload.TYPE, DashboardOpenPayload.STREAM_CODEC,
                DashboardHandlers::open);
        registrar.playToServer(DashboardActionPayload.TYPE, DashboardActionPayload.STREAM_CODEC,
                DashboardHandlers::action);
        registrar.playToServer(DashboardApplyPayload.TYPE, DashboardApplyPayload.STREAM_CODEC,
                DashboardHandlers::apply);
        registrar.playToServer(DashboardRefreshPayload.TYPE, DashboardRefreshPayload.STREAM_CODEC,
                DashboardHandlers::refresh);
    }

    /**
     * Рассылает всем актуальный список конвертеров.
     *
     * <p>Список общий на всех: конвертеры — общественные обменники, и прятать
     * их друг от друга смысла нет.
     */
    public static void broadcastMarkers(MinecraftServer server) {
        List<ConverterMarkersPayload.Marker> markers = collect(server);
        if (markers != null) {
            PacketDistributor.sendToAllPlayers(new ConverterMarkersPayload(markers));
        }
    }

    public static void sendMarkers(ServerPlayer player) {
        List<ConverterMarkersPayload.Marker> markers = collect(player.server);
        if (markers != null) {
            PacketDistributor.sendToPlayer(player, new ConverterMarkersPayload(markers));
        }
    }

    /**
     * Собирает метки по указателю.
     *
     * <p>Подпись берётся у самого конвертера, а её знает только загруженный
     * блок. У выгруженного остаётся название блока по умолчанию — это честнее,
     * чем держать вторую копию подписи в указателе и следить за её
     * рассинхроном.
     */
    private static List<ConverterMarkersPayload.Marker> collect(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        List<ConverterMarkersPayload.Marker> markers = new ArrayList<>();
        for (ConverterIndex.Entry entry : ConverterIndex.all(server)) {
            ServerLevel level = server.getLevel(entry.dimension());
            String label = "";
            if (level != null && level.hasChunkAt(entry.pos())
                    && level.getBlockEntity(entry.pos()) instanceof ConverterBlockEntity converter) {
                label = converter.label();
            }
            markers.add(new ConverterMarkersPayload.Marker(entry.dimension(), entry.pos(), label));
        }
        return markers;
    }
}
