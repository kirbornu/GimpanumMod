package com.kirbornu.gimpanum.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Команда FTB, привязанная к Ядру, вместе со снимком её состава.
 *
 * <p>Состав снимается в момент привязки и дальше не меняется: так решено
 * намеренно, чтобы расклад боя был предсказуем и не зависел от того, кто
 * вступил в команду после настройки. Перепривязать команду — значит обновить
 * снимок.
 *
 * @param teamName отображаемое имя команды на момент привязки
 * @param members  ники участников на момент привязки
 */
public record BoundTeam(String teamName, List<String> members) {

    public static final Codec<BoundTeam> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("team_name").forGetter(BoundTeam::teamName),
            Codec.STRING.listOf().optionalFieldOf("members", List.of()).forGetter(BoundTeam::members)
    ).apply(instance, BoundTeam::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoundTeam> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BoundTeam::teamName,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BoundTeam::members,
                    BoundTeam::new
            );

    public BoundTeam {
        members = List.copyOf(members);
    }
}
