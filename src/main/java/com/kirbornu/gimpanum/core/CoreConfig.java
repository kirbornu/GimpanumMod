package com.kirbornu.gimpanum.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Настройка Ядра: кого оно уносит с собой и чем это сопровождается.
 *
 * <p>Неизменяемый снимок — при разрушении копия уезжает в арбитр и переживает
 * исчезновение блок-сущности. Читать конфигурацию после удаления блока уже
 * неоткуда, поэтому снимать её надо заранее.
 *
 * @param boundPlayers ники привязанных игроков; порядок сохраняется, он же
 *                     попадёт в Печать
 * @param commands     команды, выполняемые для каждого привязанного игрока
 * @param explosionPower мощность взрыва; 4.0 — как у динамита
 * @param explosionFire оставлять ли огонь
 */
public record CoreConfig(
        List<String> boundPlayers,
        List<String> commands,
        float explosionPower,
        boolean explosionFire
) {

    public static final float DEFAULT_POWER = 4.0F;

    public static final CoreConfig EMPTY =
            new CoreConfig(List.of(), List.of(), DEFAULT_POWER, true);

    public static final Codec<CoreConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("bound_players", List.of()).forGetter(CoreConfig::boundPlayers),
            Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(CoreConfig::commands),
            Codec.FLOAT.optionalFieldOf("explosion_power", DEFAULT_POWER).forGetter(CoreConfig::explosionPower),
            Codec.BOOL.optionalFieldOf("explosion_fire", true).forGetter(CoreConfig::explosionFire)
    ).apply(instance, CoreConfig::new));

    public CoreConfig {
        boundPlayers = List.copyOf(boundPlayers);
        commands = List.copyOf(commands);
    }

    public CoreConfig withBoundPlayers(List<String> names) {
        return new CoreConfig(names, commands, explosionPower, explosionFire);
    }

    public CoreConfig withCommands(List<String> newCommands) {
        return new CoreConfig(boundPlayers, newCommands, explosionPower, explosionFire);
    }

    public CoreConfig withExplosion(float power, boolean fire) {
        return new CoreConfig(boundPlayers, commands, power, fire);
    }

    public boolean hasBoundPlayers() {
        return !boundPlayers.isEmpty();
    }
}
