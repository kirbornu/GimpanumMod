package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.item.SealContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Настройка одного Ядра: как оно называется, кого уносит с собой и как себя
 * ведёт.
 *
 * <p>Неизменяемый снимок — при разрушении копия уезжает в арбитр и переживает
 * исчезновение блок-сущности. Читать настройку после удаления блока уже
 * неоткуда, поэтому снимать её надо заранее.
 *
 * @param name             условное имя Ядра для команд; уникально среди
 *                         загруженных Ядер
 * @param boundPlayers     ники, привязанные поимённо
 * @param boundTeams       привязанные команды FTB со снимками составов
 * @param commands         команды, выполняемые для каждого привязанного игрока
 * @param sealPostfix      приписка к названию Печати, обычно название экипажа
 * @param armed            снят ли предохранитель; пока не снят, гибель Ядра
 *                         не влечёт последствий
 * @param invulnerable     неразрушимо и недвижимо, как бедрок
 * @param explosionEnabled взрывается ли Ядро при гибели
 * @param explosionPower   мощность взрыва; 4.0 — как у динамита
 * @param explosionFire    оставлять ли огонь
 */
public record CoreConfig(
        String name,
        List<String> boundPlayers,
        List<BoundTeam> boundTeams,
        List<String> commands,
        Optional<String> sealPostfix,
        boolean armed,
        boolean invulnerable,
        boolean explosionEnabled,
        float explosionPower,
        boolean explosionFire
) {

    public static final float DEFAULT_POWER = 4.0F;

    public static final CoreConfig EMPTY = new CoreConfig(
            "", List.of(), List.of(), List.of(), Optional.empty(),
            false, false, true, DEFAULT_POWER, true);

    public static final Codec<CoreConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(CoreConfig::name),
            Codec.STRING.listOf().optionalFieldOf("bound_players", List.of()).forGetter(CoreConfig::boundPlayers),
            BoundTeam.CODEC.listOf().optionalFieldOf("bound_teams", List.of()).forGetter(CoreConfig::boundTeams),
            Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(CoreConfig::commands),
            Codec.STRING.optionalFieldOf("seal_postfix").forGetter(CoreConfig::sealPostfix),
            Codec.BOOL.optionalFieldOf("armed", false).forGetter(CoreConfig::armed),
            Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(CoreConfig::invulnerable),
            Codec.BOOL.optionalFieldOf("explosion_enabled", true).forGetter(CoreConfig::explosionEnabled),
            Codec.FLOAT.optionalFieldOf("explosion_power", DEFAULT_POWER).forGetter(CoreConfig::explosionPower),
            Codec.BOOL.optionalFieldOf("explosion_fire", true).forGetter(CoreConfig::explosionFire)
    ).apply(instance, CoreConfig::new));

    public CoreConfig {
        boundPlayers = List.copyOf(boundPlayers);
        boundTeams = List.copyOf(boundTeams);
        commands = List.copyOf(commands);
    }

    public CoreConfig withName(String newName) {
        return new CoreConfig(newName, boundPlayers, boundTeams, commands, sealPostfix,
                armed, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withBoundPlayers(List<String> names) {
        return new CoreConfig(name, names, boundTeams, commands, sealPostfix,
                armed, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withBoundTeams(List<BoundTeam> teams) {
        return new CoreConfig(name, boundPlayers, teams, commands, sealPostfix,
                armed, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withCommands(List<String> newCommands) {
        return new CoreConfig(name, boundPlayers, boundTeams, newCommands, sealPostfix,
                armed, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withSealPostfix(Optional<String> postfix) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, postfix,
                armed, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withArmed(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix,
                value, invulnerable, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withInvulnerable(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix,
                armed, value, explosionEnabled, explosionPower, explosionFire);
    }

    public CoreConfig withExplosionEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix,
                armed, invulnerable, value, explosionPower, explosionFire);
    }

    public CoreConfig withExplosion(float power, boolean fire) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix,
                armed, invulnerable, explosionEnabled, power, fire);
    }

    /** Содержимое будущей Печати. */
    public SealContents sealContents() {
        return new SealContents(boundPlayers, boundTeams, sealPostfix);
    }
}
