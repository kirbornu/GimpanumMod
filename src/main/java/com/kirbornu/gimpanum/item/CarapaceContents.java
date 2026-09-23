package com.kirbornu.gimpanum.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * Что лежит в Панцире Склава: вид вещи и сколько её.
 *
 * <p>Пустого содержимого не бывает — у пустого панциря компонента нет вовсе.
 * Так вид сбрасывается сам, когда вынули последнее.
 *
 * <p>Сравнение своё, а не записи: у {@link ItemStack} нет равенства по
 * значению, а по равенству компонентов игра решает, одинаковы ли два
 * предмета, — без него два панциря с одним и тем же внутри не совпали бы.
 *
 * @param kind  образец вещи, по одной штуке
 * @param count сколько таких внутри
 */
public record CarapaceContents(ItemStack kind, int count) {

    public static final Codec<CarapaceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("kind").forGetter(CarapaceContents::kind),
            ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(CarapaceContents::count)
    ).apply(instance, CarapaceContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarapaceContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, CarapaceContents::kind,
            ByteBufCodecs.VAR_INT, CarapaceContents::count,
            CarapaceContents::new);

    public CarapaceContents {
        kind = kind.copyWithCount(1);
    }

    /** Та же ли это вещь, включая имя, чары и прочие компоненты. */
    public boolean holds(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(kind, stack);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CarapaceContents that && count == that.count && holds(that.kind);
    }

    @Override
    public int hashCode() {
        return 31 * ItemStack.hashItemAndComponents(kind) + count;
    }
}
