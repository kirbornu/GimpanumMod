package com.kirbornu.gimpanum.magic.network;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.cast.CooldownView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер — клиенту: магия перезаряжается ещё {@code ticks} тиков. Только для
 * полоски на табличках; решает, можно ли колдовать, всегда сервер.
 */
public record MagicCooldownPayload(int ticks) implements CustomPacketPayload {

    public static final Type<MagicCooldownPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gimpanum.MOD_ID, "magic_cooldown"));

    public static final StreamCodec<ByteBuf, MagicCooldownPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MagicCooldownPayload::new, MagicCooldownPayload::ticks);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MagicCooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CooldownView.start(payload.ticks()));
    }
}
