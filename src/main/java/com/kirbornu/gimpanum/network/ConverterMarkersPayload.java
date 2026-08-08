package com.kirbornu.gimpanum.network;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Список Фонос-конвертеров, отправляемый клиенту для меток на карте.
 *
 * <p>Иначе клиент о конвертерах ничего не знает: они разбросаны по карте и
 * почти все выгружены. Вейпоинты у Xaero целиком клиентские, серверного
 * механизма разослать метки у него нет — поэтому список едет своим пакетом, а
 * клиентская часть мода уже кладёт его в миникарту.
 *
 * @param markers все известные серверу конвертеры, включая другие измерения:
 *                игрок может провалиться в портал, и заново спрашивать сервер
 *                тогда не придётся
 */
public record ConverterMarkersPayload(List<Marker> markers) implements CustomPacketPayload {

    public static final Type<ConverterMarkersPayload> TYPE =
            new Type<>(Gimpanum.id("converter_markers"));

    /** Одна метка: где стоит конвертер и как он подписан. */
    public record Marker(ResourceKey<Level> dimension, BlockPos pos, String label) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Marker> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION),
                        Marker::dimension,
                        BlockPos.STREAM_CODEC, Marker::pos,
                        ByteBufCodecs.STRING_UTF8, Marker::label,
                        Marker::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterMarkersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Marker.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ConverterMarkersPayload::markers,
                    ConverterMarkersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
