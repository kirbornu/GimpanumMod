package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.particle.SpellParticles;
import com.kirbornu.gimpanum.magic.spell.Anchor;
import com.kirbornu.gimpanum.magic.spell.Region;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

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

    // --- выбор целей --------------------------------------------------------

    /** {@code target_caster}: цель — сам кастер, что бы ни было выбрано раньше. */
    static final class TargetCaster extends Behavior {
        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            LivingEntity caster = context.session().caster();
            if (caster == null || !caster.isAlive() || caster.level() != context.level()) {
                return;
            }
            context.chain(anchor -> List.of(Anchor.of(caster)));
            mark(context);
        }
    }

    /**
     * {@code target_nearest}: от каждого якоря — ближайшая живая сущность в
     * радиусе {@code radius}, кроме самого якоря; при {@code excludeCaster} —
     * и кроме кастера.
     */
    static final class TargetNearest extends Behavior {
        private final double radius;
        private final boolean excludeCaster;

        TargetNearest(double radius, boolean excludeCaster) {
            this.radius = radius;
            this.excludeCaster = excludeCaster;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            LivingEntity caster = context.session().caster();
            context.chain(anchor -> {
                Vec3 at = anchor.point();
                return context.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                                e -> e.isAlive() && !e.isSpectator() && e != anchor.entity()
                                        && !(excludeCaster && e == caster)
                                        && e.getBoundingBox().getCenter().distanceTo(at) <= radius)
                        .stream()
                        .min(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(at)))
                        .map(e -> {
                            SpellParticles.line(context.level(), context.particles(), at, e.getBoundingBox().getCenter());
                            return List.of(Anchor.of(e));
                        })
                        .orElse(List.of());
            });
        }
    }

    /**
     * {@code target_filter}: оставить из уже выбранных целей (или из цели по
     * умолчанию) только подходящие под {@code filter}. В отличие от прочих
     * шагов выбора, пустой результат не откатывается: фильтр — это запрет, и
     * «только чудовища» без чудовищ рядом не должны вместо них задеть кастера.
     */
    static final class TargetFilter extends Behavior {
        /** Цель и кастер → подходит ли цель. */
        private final BiPredicate<LivingEntity, LivingEntity> filter;

        TargetFilter(BiPredicate<LivingEntity, LivingEntity> filter) {
            this.filter = filter;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            LivingEntity caster = context.session().caster();
            List<LivingEntity> kept = context.targets().stream().filter(e -> filter.test(e, caster)).toList();
            context.selectStrictly(kept);
            for (LivingEntity target : kept) {
                SpellParticles.burst(context.level(), context.particles(), target.getBoundingBox().getCenter());
            }
        }
    }

    /**
     * {@code target_limit}: оставить не больше {@code count} целей — ближайших
     * к сущности-заклинанию.
     */
    static final class TargetLimit extends Behavior {
        private final int count;

        TargetLimit(int count) {
            this.count = count;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            Vec3 at = context.entity().position();
            List<LivingEntity> kept = context.targets().stream()
                    .sorted(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(at)))
                    .limit(count)
                    .toList();
            context.selectStrictly(kept);
            for (LivingEntity target : kept) {
                SpellParticles.burst(context.level(), context.particles(), target.getBoundingBox().getCenter());
            }
        }
    }

    // --- формы ---------------------------------------------------------------

    /**
     * {@code shape_line}: луч длиной {@code length} и шириной {@code width} от
     * каждого якоря (от глаз, если якорь — существо) по взгляду кастера в
     * момент каста.
     */
    static final class ShapeLine extends Behavior {
        private final double length;
        private final double width;

        ShapeLine(double length, double width) {
            this.length = length;
            this.width = width;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.shape(anchor -> {
                Vec3 start = anchor.entity() != null ? anchor.eye() : anchor.point();
                SpellParticles.line(context.level(), context.particles(), start,
                        start.add(context.direction().scale(length)));
                return Region.line(start, context.direction(), length, width);
            });
        }
    }

    /**
     * {@code shape_ring}: кольцо радиусом {@code radius} и толщиной
     * {@code thickness} вокруг каждого якоря, один слой блоков на высоте его
     * ног (у точки — на её высоте; обычно сущность-заклинание на высоте глаз,
     * поэтому кольцу вокруг неё нужен ещё {@code shape_ground}). Существа —
     * те, кто стоит в кольце.
     */
    static final class ShapeRing extends Behavior {
        private final double radius;
        private final double thickness;

        ShapeRing(double radius, double thickness) {
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.shape(anchor -> {
                Vec3 center = anchor.entity() != null ? anchor.entity().position() : anchor.point();
                int floorY = Mth.floor(center.y);
                SpellParticles.ring(context.level(), context.particles(), new Vec3(center.x, floorY + 0.1, center.z),
                        radius);
                return Region.ring(center, floorY, radius, thickness);
            });
        }
    }

    /** {@code shape_box}: прямоугольник {@code x × y × z} блоков с центром в каждом якоре. */
    static final class ShapeBox extends Behavior {
        private final double x;
        private final double y;
        private final double z;

        ShapeBox(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.shape(anchor -> {
                Region region = Region.box(anchor.point(), x, y, z);
                SpellParticles.box(context.level(), context.particles(), region.bounds());
                return region;
            });
        }
    }

    /**
     * {@code shape_ground}: опустить выделение блоков на землю. Каждый блок
     * выделения (без формы — блок в позиции каждой цели) сдвигается вниз до
     * первого свободного места над твёрдым, а если сам в земле — вверх до
     * первого свободного, не дальше {@code maxDepth}. Свободное — воздух и
     * заменяемое (трава, снежный слой); вода — не свободное, а поверхность.
     * Не нашлось земли в пределах — блок выпадает из выделения. Цели не меняются.
     */
    static final class ShapeGround extends Behavior {
        private final int maxDepth;

        ShapeGround(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            Set<BlockPos> source = context.blocks() != null ? context.blocks()
                    : new LinkedHashSet<>(context.blockTargets());
            Set<BlockPos> grounded = new LinkedHashSet<>();
            for (BlockPos pos : source) {
                BlockPos ground = ground(context.level(), pos);
                if (ground != null) {
                    grounded.add(ground);
                }
            }
            context.replaceBlocks(grounded);
            SpellParticles.blocks(context.level(), context.particles(), grounded);
        }

        private BlockPos ground(ServerLevel level, BlockPos start) {
            BlockPos.MutableBlockPos pos = start.mutable();
            if (!free(level, pos)) {
                for (int i = 0; i < maxDepth && !free(level, pos); i++) {
                    pos.move(0, 1, 0);
                }
                return free(level, pos) ? pos.immutable() : null;
            }
            for (int i = 0; i <= maxDepth; i++) {
                if (!free(level, pos.below())) {
                    return pos.immutable();
                }
                if (pos.getY() <= level.getMinBuildHeight()) {
                    return null;
                }
                pos.move(0, -1, 0);
            }
            return null;
        }

        private static boolean free(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.canBeReplaced() && state.getFluidState().isEmpty();
        }
    }

    // --- время и повторы ----------------------------------------------------

    /**
     * {@code repeat}: применить действия корня {@code times} раз подряд, в
     * один тик. Повторы множатся (два префикса по 3 — девять раз), но не
     * больше 100. С периодичностью — каждый период повторяет столько же.
     */
    static final class Repeat extends Behavior {
        private final int times;

        Repeat(int times) {
            this.times = times;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.multiplyRepeat(times);
            mark(context);
        }
    }

    /**
     * {@code delay}: отложить применение корня (вместе с действиями его
     * префиксов и суффиксов) на {@code seconds} с. Задержки складываются.
     * Программа заклинания не ждёт: следующий интерфикс и корень идут сразу,
     * а сущность-заклинание не исчезнет, пока отложенное не сработает. Цели
     * по умолчанию ищутся в момент срабатывания; выбранные префиксами — в
     * момент каста.
     */
    static final class Delay extends Behavior {
        private final double seconds;

        Delay(double seconds) {
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.MODIFIER;
        }

        @Override
        public void modify(SpellContext context) {
            context.addDelay(seconds);
            mark(context);
        }
    }
}
