package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.item.SealContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

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
 * <p><b>Полей ровно 16 — предел {@code RecordCodecBuilder}.</b> Следующее поле
 * в плоский список уже не влезет: придётся собрать связанные настройки во
 * вложенные записи, а это меняет структуру NBT и потребует переноса данных
 * уже расставленных Ядер.
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
 * @param sealEnabled      ронять ли Печать при гибели
 * @param sealPrice        цена Печати
 * @param explosionEnabled взрывается ли Ядро при гибели; выключено по
 *                         умолчанию — взрыв включается осознанно
 * @param explosionPower   мощность взрыва; 4.0 — как у динамита
 * @param explosionFire    оставлять ли огонь
 * @param spawnEnabled     выдаёт ли Ядро предмет через равные промежутки
 * @param spawnIntervalSeconds промежуток между выдачами, в секундах
 * @param spawnItem        что выдавать; пусто — Печать со всеми свойствами
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
        int sealPrice,
        boolean explosionEnabled,
        float explosionPower,
        boolean explosionFire,
        boolean spawnEnabled,
        int spawnIntervalSeconds,
        Optional<ResourceLocation> spawnItem
) {

    public static final float DEFAULT_POWER = 4.0F;
    public static final int DEFAULT_SPAWN_INTERVAL_SECONDS = 60;

    public static final CoreConfig EMPTY = new CoreConfig(
            "", List.of(), List.of(), List.of(), Optional.empty(),
            false, true, true,
            true, SealContents.DEFAULT_PRICE,
            false, DEFAULT_POWER, true,
            false, DEFAULT_SPAWN_INTERVAL_SECONDS, Optional.empty());

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
            Codec.INT.optionalFieldOf("seal_price", SealContents.DEFAULT_PRICE).forGetter(CoreConfig::sealPrice),
            Codec.BOOL.optionalFieldOf("explosion_enabled", false).forGetter(CoreConfig::explosionEnabled),
            Codec.FLOAT.optionalFieldOf("explosion_power", DEFAULT_POWER).forGetter(CoreConfig::explosionPower),
            Codec.BOOL.optionalFieldOf("explosion_fire", true).forGetter(CoreConfig::explosionFire),
            Codec.BOOL.optionalFieldOf("spawn_enabled", false).forGetter(CoreConfig::spawnEnabled),
            Codec.INT.optionalFieldOf("spawn_interval_seconds", DEFAULT_SPAWN_INTERVAL_SECONDS)
                    .forGetter(CoreConfig::spawnIntervalSeconds),
            ResourceLocation.CODEC.optionalFieldOf("spawn_item").forGetter(CoreConfig::spawnItem)
    ).apply(instance, CoreConfig::new));

    /** Полей больше, чем принимает composite, поэтому кодек оборачивается целиком. */
    public static final StreamCodec<ByteBuf, CoreConfig> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public CoreConfig {
        boundPlayers = List.copyOf(boundPlayers);
        boundTeams = List.copyOf(boundTeams);
        commands = List.copyOf(commands);
    }

    public CoreConfig withName(String value) {
        return new CoreConfig(value, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withBoundPlayers(List<String> value) {
        return new CoreConfig(name, value, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withBoundTeams(List<BoundTeam> value) {
        return new CoreConfig(name, boundPlayers, value, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withCommands(List<String> value) {
        return new CoreConfig(name, boundPlayers, boundTeams, value, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withSealPostfix(Optional<String> value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, value, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
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
                stillInvulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withInvulnerable(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                value, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withAutoDisableInvulnerable(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, value, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withSealEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, value, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withSealPrice(int value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, value, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withExplosionEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, value,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withExplosion(float power, boolean fire) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                power, fire, spawnEnabled, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withSpawnEnabled(boolean value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, value, spawnIntervalSeconds, spawnItem);
    }

    public CoreConfig withSpawnInterval(int seconds) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, seconds, spawnItem);
    }

    public CoreConfig withSpawnItem(Optional<ResourceLocation> value) {
        return new CoreConfig(name, boundPlayers, boundTeams, commands, sealPostfix, armed,
                invulnerable, autoDisableInvulnerable, sealEnabled, sealPrice, explosionEnabled,
                explosionPower, explosionFire, spawnEnabled, spawnIntervalSeconds, value);
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

    /** Промежуток между выдачами в тиках; не меньше одной секунды. */
    public int spawnIntervalTicks() {
        return Math.max(1, spawnIntervalSeconds) * 20;
    }

    /** Содержимое будущей Печати. */
    public SealContents sealContents() {
        return new SealContents(boundPlayers, boundTeams, sealPostfix, sealPrice);
    }
}
