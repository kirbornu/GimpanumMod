package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
     * команды, не должен получить команду дважды. Повтором считается и тот же
     * ник в другом регистре — игра их не различает.
     */
    public List<String> allPlayers() {
        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<String> unique = new ArrayList<>();
        for (String name : players) {
            if (seen.add(name)) {
                unique.add(name);
            }
        }
        for (BoundTeam team : teams) {
            for (String name : team.members()) {
                if (seen.add(name)) {
                    unique.add(name);
                }
            }
        }
        return unique;
    }
}
