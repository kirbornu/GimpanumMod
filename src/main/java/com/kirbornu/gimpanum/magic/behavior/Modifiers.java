package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.particle.SpellParticles;
import com.kirbornu.gimpanum.magic.spell.Anchor;
import com.kirbornu.gimpanum.magic.spell.Region;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Поведения, меняющие контекст: множители, цели, формы. */
final class Modifiers {

    private Modifiers() {
    }

    /** Отметка на месте сущности-заклинания: префикс без формы иначе был бы невидим. */
    private static void mark(SpellContext context) {
        SpellParticles.ring(context.level(), context.particles(), context.origin(), 0.6);
    }

    /** {@code extend_duration}: длительные эффекты корня длятся в {@code factor} раз дольше. */
    static final class ExtendDuration extends Behavior {
        private final double factor;

        ExtendDuration(double factor) {
            this.factor = factor;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.multiplyDuration(factor);
            mark(context);
        }
    }

    /** {@code periodic_if_instant}: мгновенный корень повторяется раз в {@code interval} с {@code duration} с. */
    static final class PeriodicIfInstant extends Behavior {
        private final double interval;
        private final double duration;

        PeriodicIfInstant(double interval, double duration) {
            this.interval = interval;
            this.duration = duration;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.makePeriodic(interval, duration);
            mark(context);
        }
    }

    /** {@code amplify}: основной параметр корня больше на {@code percent} %. */
    static final class Amplify extends Behavior {
        private final double percent;

        Amplify(double percent) {
            this.percent = percent;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.multiplyPrimary(1.0 + percent / 100.0);
        }
    }

    /**
     * {@code target_look}: от каждого якоря — живая сущность под прицелом не
     * дальше {@code range}. От самой сущности-заклинания смотрит кастер: его
     * глаза и взгляд в момент каста.
     */
    static final class TargetLook extends Behavior {
        private final double range;

        TargetLook(double range) {
            this.range = range;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.chain(anchor -> {
                LivingEntity caster = context.session().caster();
                boolean fromCaster = anchor.entity() == null && caster != null && caster.isAlive()
                        && caster.level() == context.level();
                Entity looker = fromCaster ? caster : anchor.entity();
                Vec3 start = fromCaster ? caster.getEyePosition() : anchor.eye();
                Vec3 end = start.add(context.direction().scale(range));
                // Сквозь стены не целимся: луч обрывается о первый блок.
                HitResult wall = context.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, looker != null ? looker : context.entity()));
                if (wall.getType() != HitResult.Type.MISS) {
                    end = wall.getLocation();
                }
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(context.level(), context.entity(), start, end,
                        new AABB(start, end).inflate(1.0),
                        e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
                                && e != looker, 0.3F);
                if (hit == null) {
                    return List.of();
                }
                LivingEntity target = (LivingEntity) hit.getEntity();
                SpellParticles.line(context.level(), context.particles(), start, target.getBoundingBox().getCenter());
                return List.of(Anchor.of(target));
            });
        }
    }

    /** {@code target_radius}: от каждого якоря — все живые в радиусе {@code radius}. */
    static final class TargetRadius extends Behavior {
        private final double radius;
        private final boolean includeCaster;

        TargetRadius(double radius, boolean includeCaster) {
            this.radius = radius;
            this.includeCaster = includeCaster;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            LivingEntity caster = context.session().caster();
            context.chain(anchor -> {
                SpellParticles.sphere(context.level(), context.particles(), anchor.point(), radius);
                return context.level().getEntitiesOfClass(LivingEntity.class,
                                new AABB(anchor.point(), anchor.point()).inflate(radius),
                                e -> e.isAlive() && !e.isSpectator() && (includeCaster || e != caster)
                                        && e.getBoundingBox().getCenter().distanceTo(anchor.point()) <= radius)
                        .stream().map(Anchor::of).toList();
            });
        }
    }

    /**
     * {@code shape_cone}: конус раствором {@code angle}° и длиной {@code length}
     * от каждого якоря по взгляду кастера в момент каста.
     */
    static final class ShapeCone extends Behavior {
        private final double angle;
        private final double length;

        ShapeCone(double angle, double length) {
            this.angle = angle;
            this.length = length;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            double half = Math.toRadians(angle / 2);
            context.shape(anchor -> {
                Vec3 apex = anchor.entity() != null ? anchor.eye() : anchor.point();
                SpellParticles.cone(context.level(), context.particles(), apex, context.direction(), half, length);
                return Region.cone(apex, context.direction(), half, length);
            });
        }
    }

    /** {@code shape_sphere}: шар радиуса {@code radius} вокруг каждого якоря. */
    static final class ShapeSphere extends Behavior {
        private final double radius;

        ShapeSphere(double radius) {
            this.radius = radius;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.shape(anchor -> {
                SpellParticles.sphere(context.level(), context.particles(), anchor.point(), radius);
                return Region.sphere(anchor.point(), radius);
            });
        }
    }
}
