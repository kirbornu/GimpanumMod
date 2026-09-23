package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.RaditaWingsItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент прыгнул в полёте на Крыльях Радитажа — просит взмах. */
public record WingFlapPayload() implements CustomPacketPayload {

    public static final WingFlapPayload INSTANCE = new WingFlapPayload();

    public static final Type<WingFlapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gimpanum.MOD_ID, "wing_flap"));

    public static final StreamCodec<ByteBuf, WingFlapPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WingFlapPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> RaditaWingsItem.flap(player));
        }
    }
}
