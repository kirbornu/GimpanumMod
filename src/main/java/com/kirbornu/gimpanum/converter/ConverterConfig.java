package com.kirbornu.gimpanum.converter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

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
 * <p>Ненастроенный конвертер бездействует: пока не задан ни приём, ни выдача,
 * он не трогает брошенные предметы. Так свежепоставленный блок не съедает то,
 * что рядом с ним уронили.
 *
 * @param input       что принимает; пусто — конвертер не принимает ничего
 * @param quota       сколько принятого нужно набрать на одну выдачу
 * @param output      что выдаёт; пусто — конвертер ничего не выдаёт
 * @param outputCount сколько штук выдаёт за одну выполненную квоту
 * @param label       подпись для игроков и будущих меток на карте
 */
public record ConverterConfig(
        Optional<ResourceLocation> input,
        int quota,
        Optional<ResourceLocation> output,
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

    public static final Codec<ConverterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("input").forGetter(ConverterConfig::input),
            Codec.INT.optionalFieldOf("quota", DEFAULT_QUOTA).forGetter(ConverterConfig::quota),
            ResourceLocation.CODEC.optionalFieldOf("output").forGetter(ConverterConfig::output),
            Codec.INT.optionalFieldOf("output_count", 1).forGetter(ConverterConfig::outputCount),
            Codec.STRING.optionalFieldOf("label").forGetter(ConverterConfig::label)
    ).apply(instance, ConverterConfig::new));

    /** Настроен ли обмен целиком: без любой из сторон конвертер бездействует. */
    public boolean isOperational() {
        return input.isPresent() && output.isPresent() && effectiveQuota() > 0;
    }

    /** Квота не может быть нулевой: иначе одна брошенная стопка выдавала бы бесконечность. */
    public int effectiveQuota() {
        return Math.max(1, quota);
    }

    public ConverterConfig withInput(Optional<ResourceLocation> value, int newQuota) {
        return new ConverterConfig(value, newQuota, output, outputCount, label);
    }

    public ConverterConfig withOutput(Optional<ResourceLocation> value, int count) {
        return new ConverterConfig(input, quota, value, count, label);
    }

    public ConverterConfig withLabel(Optional<String> value) {
        return new ConverterConfig(input, quota, output, outputCount, value);
    }
}
