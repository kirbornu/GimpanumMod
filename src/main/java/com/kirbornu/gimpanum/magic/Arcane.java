package com.kirbornu.gimpanum.magic;

import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.config.ArcaneDef;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.ArcaneParticles;
import com.kirbornu.gimpanum.magic.particle.ParticleLayer;
import com.kirbornu.gimpanum.magic.sound.ArcaneSounds;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Аркан, готовый к исполнению: поведения уже собраны из настройки, частицы разобраны. */
public final class Arcane {

    private final ArcaneDef def;
    private final Map<Role, List<Behavior>> behaviors;
    private final ArcaneParticles particles;
    private final ArcaneSounds sounds;

    public Arcane(ArcaneDef def, Map<Role, List<Behavior>> behaviors, ArcaneParticles particles) {
        this(def, behaviors, particles, ArcaneSounds.NONE);
    }

    public Arcane(ArcaneDef def, Map<Role, List<Behavior>> behaviors, ArcaneParticles particles,
                  ArcaneSounds sounds) {
        this.def = def;
        this.behaviors = new EnumMap<>(behaviors);
        this.particles = particles;
        this.sounds = sounds;
    }

    public String id() {
        return def.id();
    }

    public String name() {
        return def.name();
    }

    public int order() {
        return def.order();
    }

    public List<Behavior> behaviors(Role role) {
        return behaviors.getOrDefault(role, List.of());
    }

    public ParticleLayer particles(Role role) {
        return particles.layer(role);
    }

    public ArcaneSounds sounds() {
        return sounds;
    }

    /**
     * Есть ли у корня длительные эффекты.
     *
     * <p>Мгновенный корень — тот, у которого их нет вовсе: такой огненный
     * префикс превращает в периодический, а длительный — продлевает.
     */
    public boolean rootIsLasting() {
        return behaviors(Role.ROOT).stream().anyMatch(Behavior::lasting);
    }
}
