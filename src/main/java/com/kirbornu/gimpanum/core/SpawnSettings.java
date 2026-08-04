package com.kirbornu.gimpanum.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Настройка периодической выдачи, собранная в отдельную запись.
 *
 * <p>Причина сугубо техническая: {@code RecordCodecBuilder} принимает не больше
 * шестнадцати полей, а {@link CoreConfig} упёрлась в этот предел. Вынесенная
 * запись занимает в списке одно место вместо четырёх и оставляет запас на
 * будущие настройки.
 *
 * <p>Кодек здесь именно {@link MapCodec}: он вписывается в общую карту, а не в
 * подобъект, поэтому в NBT поля лежат по тем же ключам, что и раньше, и уже
 * расставленные Ядра сохраняют свою настройку.
 *
 * @param enabled         выдаёт ли Ядро предмет через равные промежутки
 * @param intervalSeconds промежуток между выдачами, в секундах
 * @param count           сколько штук выдавать за раз
 * @param item            что выдавать; пусто — Печать со всеми свойствами
 */
public record SpawnSettings(boolean enabled, int intervalSeconds, int count,
                            Optional<ResourceLocation> item) {

    public static final int DEFAULT_INTERVAL_SECONDS = 60;

    /**
     * Потолок на количество за раз.
     *
     * <p>Предметы, которые не влезли в одну стопку, выпадают отдельными
     * сущностями, а у Печати стопка вообще единичная — без ограничения одна
     * опечатка в команде обернулась бы тысячами сущностей в одном чанке.
     */
    public static final int MAX_COUNT = 256;

    public static final SpawnSettings DISABLED =
            new SpawnSettings(false, DEFAULT_INTERVAL_SECONDS, 1, Optional.empty());

    public static final MapCodec<SpawnSettings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("spawn_enabled", false).forGetter(SpawnSettings::enabled),
            Codec.INT.optionalFieldOf("spawn_interval_seconds", DEFAULT_INTERVAL_SECONDS)
                    .forGetter(SpawnSettings::intervalSeconds),
            Codec.INT.optionalFieldOf("spawn_count", 1).forGetter(SpawnSettings::count),
            ResourceLocation.CODEC.optionalFieldOf("spawn_item").forGetter(SpawnSettings::item)
    ).apply(instance, SpawnSettings::new));

    /** Промежуток между выдачами в тиках; не меньше одной секунды. */
    public int intervalTicks() {
        return Math.max(1, intervalSeconds) * 20;
    }

    public SpawnSettings withEnabled(boolean value) {
        return new SpawnSettings(value, intervalSeconds, count, item);
    }

    public SpawnSettings withIntervalSeconds(int value) {
        return new SpawnSettings(enabled, value, count, item);
    }

    public SpawnSettings withCount(int value) {
        return new SpawnSettings(enabled, intervalSeconds, value, item);
    }

    public SpawnSettings withItem(Optional<ResourceLocation> value) {
        return new SpawnSettings(enabled, intervalSeconds, count, value);
    }
}
