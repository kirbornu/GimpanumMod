package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.core.Program;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.ParticleLayer;
import com.kirbornu.gimpanum.magic.particle.SpellParticles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Применение одного корня со всем, что к нему привязано.
 *
 * <p>Порядок: сначала все изменения контекста — префиксы слева направо,
 * затем суффиксы слева направо, затем изменения самого корня. Потом действия:
 * действия префиксов, корня, суффиксов. Так «усилить» успевает поднять
 * основной параметр до того, как корень его прочтёт, а снятие эффектов
 * суффиксом идёт после того, как корень их наложил, и может их пощадить.
 */
public final class SpellExecutor {

    private SpellExecutor() {
    }

    /** Корень шага программы со всеми префиксами и суффиксами. */
    public static void apply(SpellEntity entity, Program.Step<Arcane> step) {
        SpellContext context = new SpellContext(entity.session(), entity, step.root());
        for (Arcane prefix : step.prefixes()) {
            modify(context, prefix, Role.PREFIX);
        }
        for (Arcane suffix : step.suffixes()) {
            modify(context, suffix, Role.SUFFIX);
        }
        modify(context, step.root(), Role.ROOT);

        for (Arcane prefix : step.prefixes()) {
            act(context, prefix, Role.PREFIX);
        }
        if (context.periodic() && !step.root().rootIsLasting()) {
            // Мгновенный корень стал периодическим: первый раз — сейчас,
            // остальные — по таймеру сущности. Префиксы и суффиксы при этом
            // срабатывают один раз: периодическим стал корень, а не всё слово.
            int interval = Math.max(1, (int) Math.round(context.periodicInterval() * 20));
            int times = (int) Math.ceil(context.periodicDuration() / context.periodicInterval() - 1.0E-9);
            rootActions(context);
            entity.addRepeater(interval, times - 1, () -> rootActions(context));
        } else {
            rootActions(context);
        }
        for (Arcane suffix : step.suffixes()) {
            act(context, suffix, Role.SUFFIX);
        }
    }

    /**
     * Только корень, без префиксов и суффиксов: так работает копия,
     * порождённая огненным суффиксом. Цель — заново по умолчанию от копии.
     */
    public static void applyRootOnly(SpellEntity entity, Arcane root) {
        SpellContext context = new SpellContext(entity.session(), entity, root);
        modify(context, root, Role.ROOT);
        rootActions(context);
    }

    private static void modify(SpellContext context, Arcane arcane, Role role) {
        for (Behavior behavior : arcane.behaviors(role)) {
            if (behavior.kind() == Behavior.Kind.MODIFIER) {
                context.enter(arcane, role);
                behavior.modify(context);
            }
        }
    }

    private static void act(SpellContext context, Arcane arcane, Role role) {
        boolean acted = false;
        for (Behavior behavior : arcane.behaviors(role)) {
            if (behavior.kind() == Behavior.Kind.ACTION) {
                context.enter(arcane, role);
                behavior.act(context, false);
                acted = true;
            }
        }
        if (role == Role.SUFFIX) {
            // Спираль суффикса рисуется, даже если он только менял контекст
            // («усилить»): иначе по картинке не понять, что суффикс был.
            spirals(context, arcane.particles(Role.SUFFIX));
        } else if (acted) {
            bursts(context, arcane.particles(role));
        }
    }

    private static void rootActions(SpellContext context) {
        Arcane root = context.root();
        for (Behavior behavior : root.behaviors(Role.ROOT)) {
            if (behavior.kind() == Behavior.Kind.ACTION) {
                context.enter(root, Role.ROOT);
                behavior.act(context, true);
            }
        }
        bursts(context, root.particles(Role.ROOT));
    }

    /** Вспышки корня — на каждой цели, а если корень работал с формой, ещё и над её блоками. */
    private static void bursts(SpellContext context, ParticleLayer layer) {
        if (layer == null) {
            return;
        }
        List<LivingEntity> targets = context.targets();
        for (LivingEntity target : targets) {
            SpellParticles.burst(context.level(), layer, target.getBoundingBox().getCenter());
        }
        if (targets.isEmpty()) {
            SpellParticles.blocks(context.level(), layer, context.blockTargets());
        }
    }

    private static void spirals(SpellContext context, ParticleLayer layer) {
        if (layer == null) {
            return;
        }
        List<LivingEntity> targets = context.targets();
        for (LivingEntity target : targets) {
            SpellParticles.spiral(context.level(), layer, target.position(), target.getBbWidth() * 0.8 + 0.3,
                    target.getBbHeight());
        }
        if (targets.isEmpty()) {
            Vec3 at = context.entity().position();
            SpellParticles.spiral(context.level(), layer, at, 0.6, 1.0);
        }
    }
}
