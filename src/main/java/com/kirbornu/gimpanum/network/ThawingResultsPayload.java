package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Содержимое Замороженной органики, отправляемое клиенту.
 *
 * <p>Список живёт в конфиге сервера, а показать его нужно в просмотрщике
 * рецептов, то есть на клиенте. Без пересылки клиент знает только результат из
 * json рецепта — одну строку-заглушку, которая честно не описывает ничего.
 *
 * <p>Уходит при входе игрока и повторно после {@code /gimpanum config reload}:
 * правка файла обязана быть видна сразу, а не после перезахода.
 */
public record ThawingResultsPayload(List<ThawedOrganics.Find> finds) implements CustomPacketPayload {

    public static final Type<ThawingResultsPayload> TYPE = new Type<>(Gimpanum.id("thawing_results"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThawingResultsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ThawedOrganics.Find.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ThawingResultsPayload::finds,
                    ThawingResultsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
