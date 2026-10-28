package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.magic.config.MagicConfig;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Общее на одно заклинание: кто кастовал, куда смотрел, сколько уже наделал.
 *
 * <p>Одна сессия на все сущности заклинания — и на главную, и на копии. Пределы
 * блоков и сущностей считаются на заклинание целиком, иначе огненный суффикс,
 * порождающий копии, обходил бы их.
 *
 * <p>Настройка снимается в момент каста: перезагрузка файлов посреди
 * заклинания не должна смешать в нём старые арканы с новыми.
 */
public final class SpellSession {

    private final ServerLevel level;
    private final LivingEntity caster;
    private final Vec3 direction;
    private final MagicConfig.Loaded config;

    private int blocksChanged;
    private int entities;
    private final Map<UUID, Set<Holder<MobEffect>>> appliedEffects = new HashMap<>();
    private final Set<UUID> ignited = new HashSet<>();

    public SpellSession(ServerLevel level, LivingEntity caster, Vec3 direction, MagicConfig.Loaded config) {
        this.level = level;
        this.caster = caster;
        this.direction = direction.lengthSqr() > 1.0E-8 ? direction.normalize() : new Vec3(0, 0, 1);
        this.config = config;
    }

    public ServerLevel level() {
        return level;
    }

    /** Кастер; может быть уже мёртв или вышел — проверяйте {@link LivingEntity#isRemoved()}. */
    public LivingEntity caster() {
        return caster;
    }

    /** Направление взгляда кастера в момент каста — дальше оно не меняется. */
    public Vec3 direction() {
        return direction;
    }

    public MagicConfig.Loaded config() {
        return config;
    }

    /** Занимает место под изменённый блок; {@code false} — предел заклинания исчерпан. */
    public boolean takeBlock() {
        if (blocksChanged >= config.settings().limits().blocksPerSpell()) {
            return false;
        }
        blocksChanged++;
        return true;
    }

    /** Занимает место под новую сущность заклинания; {@code false} — предел исчерпан. */
    public boolean takeEntity() {
        if (entities >= config.settings().limits().entitiesPerSpell()) {
            return false;
        }
        entities++;
        return true;
    }

    public void noteEffect(LivingEntity target, Holder<MobEffect> effect) {
        appliedEffects.computeIfAbsent(target.getUUID(), id -> new HashSet<>()).add(effect);
    }

    public boolean appliedEffect(LivingEntity target, Holder<MobEffect> effect) {
        Set<Holder<MobEffect>> effects = appliedEffects.get(target.getUUID());
        return effects != null && effects.contains(effect);
    }

    public void noteIgnited(LivingEntity target) {
        ignited.add(target.getUUID());
    }

    public boolean ignited(LivingEntity target) {
        return ignited.contains(target.getUUID());
    }
}
