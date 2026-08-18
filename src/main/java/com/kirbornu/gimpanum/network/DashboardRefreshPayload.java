package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Просьба прислать список Ядер заново. */
public record DashboardRefreshPayload() implements CustomPacketPayload {

    public static final Type<DashboardRefreshPayload> TYPE =
            new Type<>(Gimpanum.id("dashboard_refresh"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardRefreshPayload> STREAM_CODEC =
            StreamCodec.unit(new DashboardRefreshPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
