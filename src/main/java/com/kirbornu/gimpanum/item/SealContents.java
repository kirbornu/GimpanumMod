package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Что записано в Печати: кто был привязан к породившему её Ядру.
 *
 * @param players ники, привязанные поимённо
 * @param teams   привязанные команды со снимками составов
 * @param postfix приписка к названию Печати — обычно название экипажа
 * @param price   цена Печати
 */
public record SealContents(
        List<String> players,
        List<BoundTeam> teams,
        Optional<String> postfix,
        int price
) {

    public static final int DEFAULT_PRICE = 1;

    public static final SealContents EMPTY =
            new SealContents(List.of(), List.of(), Optional.empty(), DEFAULT_PRICE);

    public static final Codec<SealContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("players", List.of()).forGetter(SealContents::players),
            BoundTeam.CODEC.listOf().optionalFieldOf("teams", List.of()).forGetter(SealContents::teams),
            Codec.STRING.optionalFieldOf("postfix").forGetter(SealContents::postfix),
            Codec.INT.optionalFieldOf("price", DEFAULT_PRICE).forGetter(SealContents::price)
    ).apply(instance, SealContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SealContents> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SealContents::players,
                    BoundTeam.STREAM_CODEC.apply(ByteBufCodecs.list()), SealContents::teams,
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SealContents::postfix,
                    ByteBufCodecs.VAR_INT, SealContents::price,
                    SealContents::new
            );

    public SealContents {
        players = List.copyOf(players);
        teams = List.copyOf(teams);
    }

    public boolean isEmpty() {
        return players.isEmpty() && teams.isEmpty();
    }

    /**
     * Все затронутые ники без повторов: игрок, привязанный и лично, и в составе
     * команды, не должен получить команду дважды.
     */
    public List<String> allPlayers() {
        Set<String> unique = new LinkedHashSet<>(players);
        for (BoundTeam team : teams) {
            unique.addAll(team.members());
        }
        return new ArrayList<>(unique);
    }
}
