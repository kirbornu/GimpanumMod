package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.dashboard.CoreRow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Состояние консоли Ядер, отправляемое клиенту.
 *
 * <p>Одним пакетом и открытие окна, и обновление уже открытого: содержимое у
 * них одно и то же, а разница — только в том, есть ли окно на экране. Держать
 * два пакета ради этого различия незачем.
 *
 * @param mayEditCommands есть ли у получателя четвёртый уровень прав. Список
 *                        команд Ядра выполняется с правами оператора, поэтому
 *                        править его вправе не всякий, кому открыта консоль
 */
public record DashboardOpenPayload(List<CoreRow> rows, boolean mayEditCommands)
        implements CustomPacketPayload {

    public static final Type<DashboardOpenPayload> TYPE = new Type<>(Gimpanum.id("dashboard_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardOpenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CoreRow.STREAM_CODEC.apply(ByteBufCodecs.list()), DashboardOpenPayload::rows,
                    ByteBufCodecs.BOOL, DashboardOpenPayload::mayEditCommands,
                    DashboardOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
