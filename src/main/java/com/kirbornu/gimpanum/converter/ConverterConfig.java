package com.kirbornu.gimpanum.converter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Настройка одного Фонос-конвертера: что он принимает, сколько ему нужно и что
 * выдаёт взамен.
 *
 * <p>Конвертеры существуют ради экономики: один принимает предмет А и выдаёт Б,
 * другой принимает Б и выдаёт то, что игрокам действительно нужно. Поэтому
 * каждая сторона обмена настраивается отдельно, а сам блок ничего не знает о
 * ценности предметов.
 *
 * <p>Стороны обмена задаются целыми предметами, а не идентификаторами: валютой
 * обычно служит не голая морковка, а именованная и светящаяся, и отличать одну
 * от другой конвертер обязан. Поэтому приём сверяет и компоненты тоже —
 * см. {@link #matchesInput(ItemStack)}.
 *
 * <p>Ненастроенный конвертер бездействует: пока не задан ни приём, ни выдача,
 * он не трогает брошенные предметы. Так свежепоставленный блок не съедает то,
 * что рядом с ним уронили.
 *
 * <p><b>Хранимые {@link ItemStack} наружу отдаются только копиями.</b>
 * {@code ItemStack} изменяем, а настройка обязана оставаться снимком.
 *
 * @param input       образец принимаемого предмета в одном экземпляре
 * @param quota       сколько принятого нужно набрать на одну выдачу
 * @param output      образец выдаваемого предмета в одном экземпляре
 * @param outputCount сколько штук выдаётся за одну выполненную квоту
 * @param label       подпись для игроков и для меток на карте
 */
public record ConverterConfig(
        Optional<ItemStack> input,
        int quota,
        Optional<ItemStack> output,
        int outputCount,
        Optional<String> label
) {

    public static final int DEFAULT_QUOTA = 100;

    /**
     * Потолок на выдачу за раз.
     *
     * <p>Всё, что не влезло в стопку, выпадает отдельными сущностями, поэтому
     * без ограничения одна опечатка засыпала бы чанк предметами.
     */
    public static final int MAX_OUTPUT_COUNT = 256;

    public static final ConverterConfig EMPTY =
            new ConverterConfig(Optional.empty(), DEFAULT_QUOTA, Optional.empty(), 1, Optional.empty());

    /**
     * {@code SINGLE_ITEM_CODEC} — предмет с компонентами, но без количества.
     * Количество у выдачи своё, а у образца приёма его нет вовсе.
     */
    public static final Codec<ConverterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.SINGLE_ITEM_CODEC.optionalFieldOf("input").forGetter(ConverterConfig::input),
            Codec.INT.optionalFieldOf("quota", DEFAULT_QUOTA).forGetter(ConverterConfig::quota),
            ItemStack.SINGLE_ITEM_CODEC.optionalFieldOf("output").forGetter(ConverterConfig::output),
            Codec.INT.optionalFieldOf("output_count", 1).forGetter(ConverterConfig::outputCount),
            Codec.STRING.optionalFieldOf("label").forGetter(ConverterConfig::label)
    ).apply(instance, ConverterConfig::new));

    public ConverterConfig {
        input = input.map(stack -> stack.copyWithCount(1));
        output = output.map(stack -> stack.copyWithCount(1));
    }

    /** Настроен ли обмен целиком: без любой из сторон конвертер бездействует. */
    public boolean isOperational() {
        return input.isPresent() && output.isPresent();
    }

    /** Квота не может быть нулевой: иначе одна брошенная стопка выдавала бы бесконечность. */
    public int effectiveQuota() {
        return Math.max(1, quota);
    }

    /**
     * Годится ли брошенное к приёму.
     *
     * <p>Сверяются и предмет, и все компоненты. Иначе конвертер, настроенный на
     * именную валюту, принимал бы и обычный предмет того же вида — а на этом
     * держится вся ценность валюты. Обратная сторона: у настроенного на голый
     * предмет конвертера переименованный вариант приёма не пройдёт, и это тоже
     * правильно.
     */
    public boolean matchesInput(ItemStack candidate) {
        return input.map(wanted -> ItemStack.isSameItemSameComponents(wanted, candidate)).orElse(false);
    }

    /** Копия образца выдачи в нужном количестве. */
    public ItemStack outputStack(int count) {
        return output.map(stack -> stack.copyWithCount(count)).orElse(ItemStack.EMPTY);
    }

    public ConverterConfig withInput(Optional<ItemStack> value, int newQuota) {
        return new ConverterConfig(value, newQuota, output, outputCount, label);
    }

    public ConverterConfig withOutput(Optional<ItemStack> value, int count) {
        return new ConverterConfig(input, quota, value, count, label);
    }

    public ConverterConfig withLabel(Optional<String> value) {
        return new ConverterConfig(input, quota, output, outputCount, value);
    }
}
