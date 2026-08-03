package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.item.SealContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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
 * <p>Эта же запись кладётся в предмет при копировании Ядра средней кнопкой,
 * поэтому у неё есть и сетевой кодек.
 *
 * @param name             условное имя Ядра для команд; уникально среди
 *                         загруженных Ядер
 * @param boundPlayers     ники, привязанные поимённо
 * @param boundTeams       привязанные экипажи FTB со снимками составов
 * @param commands         команды, выполняемые для каждого привязанного игрока
 * @param sealPostfix      приставка к названию Печати, обычно название экипажа
 * @param armed            снят ли предохранитель; пока не снят, гибель Ядра
 *                         не влечёт последствий
 * @param invulnerable     неразрушимо и недвижимо, как бедрок; включено по
 *                         умолчанию, чтобы свежепоставленное Ядро нельзя было
 *                         снести случайно во время стройки
 * @param autoDisableInvulnerable снимать ли неразрушимость вместе с
 *                         предохранителем; включено по умолчанию, чтобы боевое
 *                         Ядро не осталось неуязвимым по забывчивости
 * @param sealEnabled      ронять ли Печать
 * @param explosionEnabled взрывается ли Ядро при гибели; выключено по
 *                         умолчанию — взрыв включается осознанно
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
        boolean autoDisableInvulnerable,
        boolean sealEnabled,
        boolean explosionEnabled,
        float explosionPower,
        boolean explosionFire
) {

    public static final float DEFAULT_POWER = 4.0F;

    public static final CoreConfig EMPTY = new CoreConfig(
            "", List.of(), List.of(), List.of(), Optional.empty(),
            false, true, true, true, false, DEFAULT_POWER, true);

    public static final Codec<CoreConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(CoreConfig::name),
            Codec.STRING.listOf().optionalFieldOf("bound_players", List.of()).forGetter(CoreConfig::boundPlayers),
            BoundTeam.CODEC.listOf().optionalFieldOf("bound_teams", List.of()).forGetter(CoreConfig::boundTeams),
            Codec.STRING.listOf().optionalFieldOf("commands", List.of()).forGetter(CoreConfig::commands),
            Codec.STRING.optionalFieldOf("seal_postfix").forGetter(CoreConfig::sealPostfix),
            Codec.BOOL.optionalFieldOf("armed", false).forGetter(CoreConfig::armed),
            Codec.BOOL.optionalFieldOf("invulnerable", true).forGetter(CoreConfig::invulnerable),
            Codec.BOOL.optionalFieldOf("auto_disable_invulnerable", true)
                    .forGetter(CoreConfig::autoDisableInvulnerable),
            Codec.BOOL.optionalFieldOf("seal_enabled", true).forGetter(CoreConfig::sealEnabled),
            Codec.BOOL.optionalFieldOf("explosion_enabled", false).forGetter(CoreConfig::explosionEnabled),
            Codec.FLOAT.optionalFieldOf("explosion_power", DEFAULT_POWER).forGetter(CoreConfig::explosionPower),
            Codec.BOOL.optionalFieldOf("explosion_fire", true).forGetter(CoreConfig::explosionFire)
    ).apply(instance, CoreConfig::new));

    /** Полей больше, чем принимает composite, поэтому кодек оборачивается целиком. */
    public static final StreamCodec<ByteBuf, CoreConfig> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public CoreConfig {
        boundPlayers = List.copyOf(boundPlayers);
        boundTeams = List.copyOf(boundTeams);
        commands = List.copyOf(commands);
    }

    public CoreConfig withName(String newName) {
        return new CoreConfig(newName, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withBoundPlayers(List<String> names) {
        return new CoreConfig(name, names, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withBoundTeams(List<BoundTeam> teams) {
        return new CoreConfig(name, boundPlayers, teams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withCommands(List<String> newCommands) {
        return new CoreConfig(name, boundPlayers, boundTeams, newCommands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withSealPostfix(Optional<String> postfix) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, postfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    /**
     * Снимает или ставит предохранитель.
     *
     * <p>Правило автоснятия живёт здесь, а не в команде, намеренно: снятие
     * предохранителя любым путём обязано срабатывать одинаково.
     */
    public CoreConfig withArmed(boolean value) {
        boolean stillInvulnerable = value && autoDisableInvulnerable ? false : invulnerable;
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, value,
                stillInvulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withInvulnerable(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                value, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withAutoDisableInvulnerable(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, value, sealEnabled, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withSealEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, value, explosionEnabled,
                explosionPower, explosionFire);
    }

    public CoreConfig withExplosionEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, value,
                explosionPower, explosionFire);
    }

    public CoreConfig withExplosion(float power, boolean fire) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, explosionEnabled,
                power, fire);
    }

    /**
     * Копия для переноса в предмет и обратно.
     *
     * <p>Имя намеренно сбрасывается: оно обязано быть уникальным среди
     * загруженных Ядер, и поставленная копия получит своё собственное. Прочие
     * настройки переносятся целиком.
     */
    public CoreConfig asTemplate() {
        return withName("");
    }

    /** Есть ли что переносить: пустой шаблон в предмет класть незачем. */
    public boolean isDefaultTemplate() {
        return asTemplate().equals(EMPTY);
    }

    /** Содержимое будущей Печати. */
    public SealContents sealContents() {
        return new SealContents(boundPlayers, boundTeams, sealPostfix);
    }
}
