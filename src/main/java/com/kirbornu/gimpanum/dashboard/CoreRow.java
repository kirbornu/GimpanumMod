package com.kirbornu.gimpanum.dashboard;

import com.kirbornu.gimpanum.core.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Строка списка в консоли: всё о Ядре, что нужно показать и отредактировать.
 *
 * <p>Настройка едет целиком, а не по полям: консоль правит её на клиенте и
 * возвращает обратно, и любое новое поле {@link CoreConfig} появляется в
 * консоли само, без правки пакета.
 *
 * <p>Место — из указателя, поэтому в списке есть и Ядра в выгруженных чанках;
 * {@code loaded} говорит, к какому из них действие дойдёт мгновенно, а к
 * какому — через чтение чанка с диска.
 *
 * <p>Кодек написан руками: полей семь, а {@code StreamCodec.composite} умеет
 * не больше шести.
 */
public record CoreRow(UUID id, String name, ResourceLocation dimension, BlockPos pos,
                      boolean loaded, boolean locked, CoreConfig config) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreRow> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CoreRow decode(RegistryFriendlyByteBuf buffer) {
                    return new CoreRow(
                            UUIDUtil.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ResourceLocation.STREAM_CODEC.decode(buffer),
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer),
                            CoreConfig.STREAM_CODEC.decode(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CoreRow value) {
                    UUIDUtil.STREAM_CODEC.encode(buffer, value.id());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.name());
                    ResourceLocation.STREAM_CODEC.encode(buffer, value.dimension());
                    BlockPos.STREAM_CODEC.encode(buffer, value.pos());
                    ByteBufCodecs.BOOL.encode(buffer, value.loaded());
                    ByteBufCodecs.BOOL.encode(buffer, value.locked());
                    CoreConfig.STREAM_CODEC.encode(buffer, value.config());
                }
            };
}
