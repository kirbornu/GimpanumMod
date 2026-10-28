package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.config.MagicSettings;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.SpellParticles;
import com.kirbornu.gimpanum.magic.spell.BlockEdits;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.spell.TempBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;
import java.util.List;

/** Процессы интерфикса: растянутое во времени между двумя корнями. */
final class Processes {

    private Processes() {
    }

    private static int ticks(double seconds) {
        return (int) Math.max(1, Math.round(seconds * 20));
    }

    /** {@code wait}: ждать {@code seconds} с. */
    static final class Wait extends Behavior {
        private final double seconds;

        Wait(double seconds) {
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            int[] left = {ticks(seconds)};
            return e -> --left[0] <= 0;
        }
    }

    /**
     * {@code descend}: опустить сущность-заклинание на ближайший блок под ней,
     * не дальше {@code maxDistance}, со скоростью {@code speed} блоков за тик.
     */
    static final class Descend extends Behavior {
        private final double maxDistance;
        private final double speed;

        Descend(double maxDistance, double speed) {
            this.maxDistance = maxDistance;
            this.speed = speed;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            double floor = floorBelow(entity);
            return e -> {
                double y = Math.max(floor, e.getY() - speed);
                e.setPos(e.getX(), y, e.getZ());
                return y <= floor;
            };
        }

        /** Верх первой твёрдой поверхности под сущностью — или предел спуска. */
        private double floorBelow(SpellEntity entity) {
            ServerLevel level = (ServerLevel) entity.level();
            double limit = entity.getY() - maxDistance;
            BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
            while (pos.getY() >= level.getMinBuildHeight() && pos.getY() + 1 > limit) {
                BlockState state = level.getBlockState(pos);
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty()) {
                    double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                    // Блок, верх которого выше сущности, — тот, в котором она
                    // висит (плита, забор): это не пол под ней, ищем ниже.
                    if (top <= entity.getY() + 1.0E-6) {
                        return Math.max(top, limit);
                    }
                }
                pos.move(0, -1, 0);
            }
            return limit;
        }
    }

    /**
     * {@code home}: {@code seconds} с медленно, по {@code speed} блоков за тик,
     * двигаться к ближайшей живой сущности; при {@code excludeCaster} — кроме
     * кастера.
     *
     * <p>Дёшево по построению: цель ищется по рамке раз в N тиков (N и радиус —
     * в {@code magic.json}) и запоминается, а одновременно наводящихся
     * сущностей на сервере не больше предела. Сверх предела процесс просто
     * ждёт своё время на месте.
     */
    static final class Home extends Behavior {
        private static int active;

        private final double seconds;
        private final double speed;
        private final boolean excludeCaster;

        Home(double seconds, double speed, boolean excludeCaster) {
            this.seconds = seconds;
            this.speed = speed;
            this.excludeCaster = excludeCaster;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            MagicSettings settings = entity.session().config().settings();
            boolean homing = active < settings.limits().homingEntities();
            if (homing) {
                active++;
            }
            return new Process() {
                private int left = ticks(seconds);
                private int untilSearch;
                private LivingEntity target;
                private boolean counted = homing;

                @Override
                public boolean tick(SpellEntity e) {
                    if (counted) {
                        if (--untilSearch <= 0 || target == null || !target.isAlive() || target.level() != e.level()) {
                            target = search(e, settings);
                            untilSearch = settings.homing().searchIntervalTicks();
                        }
                        if (target != null) {
                            Vec3 to = target.getBoundingBox().getCenter().subtract(e.position());
                            double distance = to.length();
                            if (distance > 1.0E-3) {
                                e.setPos(e.position().add(to.scale(Math.min(speed, distance) / distance)));
                            }
                        }
                    }
                    return --left <= 0;
                }

                @Override
                public void stop(SpellEntity e) {
                    if (counted) {
                        counted = false;
                        active--;
                    }
                }
            };
        }

        private LivingEntity search(SpellEntity entity, MagicSettings settings) {
            double radius = settings.homing().searchRadius();
            LivingEntity caster = entity.session().caster();
            Vec3 at = entity.position();
            return entity.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                            e -> e.isAlive() && !e.isSpectator() && !(excludeCaster && e == caster))
                    .stream()
                    .min(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(at)))
                    .orElse(null);
        }
    }

    /**
     * {@code emit_light}: пока идёт интерфикс, сущность светится яркостью
     * {@code level} — временным блоком света в своей позиции, который
     * переезжает вместе с ней и убирается в конце. Интерфикс не удлиняет.
     */
    static final class EmitLight extends Behavior {
        private final int brightness;

        EmitLight(int brightness) {
            this.brightness = brightness;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, brightness);
            // Страховка на случай падения сервера: запись в TempBlocks уберёт
            // свет не позже, чем сущность обязана исчезнуть сама.
            long safety = Math.round(entity.session().config().settings().limits().entityLifetimeSeconds() * 20);
            return new Process() {
                private BlockPos lit;

                @Override
                public boolean tick(SpellEntity e) {
                    BlockPos here = e.blockPosition();
                    if (here.equals(lit)) {
                        return true;
                    }
                    clear(e);
                    ServerLevel level = (ServerLevel) e.level();
                    BlockState state = level.getBlockState(here);
                    if (state.isAir() && BlockEdits.mayEdit(e.session(), here)
                            && level.setBlock(here, light, net.minecraft.world.level.block.Block.UPDATE_ALL)) {
                        TempBlocks.add(level, here, light, state, safety);
                        lit = here.immutable();
                    }
                    return true;
                }

                @Override
                public boolean blocking() {
                    return false;
                }

                @Override
                public void stop(SpellEntity e) {
                    clear(e);
                }

                private void clear(SpellEntity e) {
                    if (lit != null) {
                        TempBlocks.removeNow((ServerLevel) e.level(), lit, light);
                        lit = null;
                    }
                }
            };
        }
    }

    // --- движение -----------------------------------------------------------

    /**
     * Сдвинуть сущность на {@code delta}, не проходя сквозь блоки.
     *
     * @return упёрлась ли она в блок
     */
    private static boolean moveBlocked(SpellEntity entity, Vec3 delta) {
        Vec3 from = entity.position();
        Vec3 to = from.add(delta);
        HitResult hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) {
            entity.setPos(to);
            return false;
        }
        // Чуть не доходя до стены — иначе сущность окажется внутри блока.
        Vec3 stop = hit.getLocation().subtract(delta.normalize().scale(0.05));
        entity.setPos(stop);
        return true;
    }

    /**
     * {@code move_forward}: лететь по взгляду кастера в момент каста на
     * {@code distance} блоков со скоростью {@code speed} блоков за тик.
     * Упёрлась в блок — интерфикс для неё закончен. Вместе со следующим
     * корнем это снаряд: «полететь вперёд, затем взорваться».
     */
    static final class MoveForward extends Behavior {
        private final double distance;
        private final double speed;

        MoveForward(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            return travel(entity.session().direction(), distance, speed);
        }
    }

    /** {@code rise}: подняться на {@code distance} блоков со скоростью {@code speed}; потолок останавливает. */
    static final class Rise extends Behavior {
        private final double distance;
        private final double speed;

        Rise(double distance, double speed) {
            this.distance = distance;
            this.speed = speed;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            return travel(new Vec3(0, 1, 0), distance, speed);
        }
    }

    private static Behavior.Process travel(Vec3 direction, double distance, double speed) {
        Vec3 axis = direction.normalize();
        double[] travelled = {0};
        return e -> {
            double step = Math.min(speed, distance - travelled[0]);
            if (step <= 0) {
                return true;
            }
            travelled[0] += step;
            return moveBlocked(e, axis.scale(step)) || travelled[0] >= distance - 1.0E-9;
        };
    }

    /**
     * {@code teleport_forward}: мгновенно переместиться по взгляду кастера на
     * {@code distance} блоков — или до первого блока на пути.
     */
    static final class TeleportForward extends Behavior {
        private final double distance;

        TeleportForward(double distance) {
            this.distance = distance;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            return e -> {
                moveBlocked(e, e.session().direction().normalize().scale(distance));
                return true;
            };
        }
    }

    /**
     * {@code follow_caster}: {@code seconds} с держаться у глаз кастера.
     * Кастер ушёл в другое измерение или погиб — сущность остаётся, где была.
     */
    static final class FollowCaster extends Behavior {
        private final double seconds;

        FollowCaster(double seconds) {
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            int[] left = {ticks(seconds)};
            return e -> {
                LivingEntity caster = e.session().caster();
                if (caster != null && caster.isAlive() && caster.level() == e.level()) {
                    e.setPos(caster.getEyePosition());
                }
                return --left[0] <= 0;
            };
        }
    }

    /**
     * {@code orbit}: {@code seconds} с кружить вокруг глаз кастера по
     * горизонтальной окружности радиуса {@code radius}, оборот за
     * {@code period} с. Сквозь блоки проходит — это не полёт, а знак.
     */
    static final class Orbit extends Behavior {
        private final double radius;
        private final double seconds;
        private final double period;

        Orbit(double radius, double seconds, double period) {
            this.radius = radius;
            this.seconds = seconds;
            this.period = period;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            int[] left = {ticks(seconds)};
            double perTick = Mth.TWO_PI / Math.max(1, period * 20);
            // Начать с той стороны, где сущность уже есть, чтобы не прыгать.
            LivingEntity caster = entity.session().caster();
            Vec3 offset = caster == null ? Vec3.ZERO : entity.position().subtract(caster.getEyePosition());
            double[] angle = {offset.horizontalDistanceSqr() > 1.0E-6 ? Math.atan2(offset.z, offset.x) : 0};
            return e -> {
                LivingEntity c = e.session().caster();
                if (c != null && c.isAlive() && c.level() == e.level()) {
                    angle[0] += perTick;
                    Vec3 center = c.getEyePosition();
                    e.setPos(center.x + Math.cos(angle[0]) * radius, center.y, center.z + Math.sin(angle[0]) * radius);
                }
                return --left[0] <= 0;
            };
        }
    }

    // --- ожидание и аура ------------------------------------------------------

    /**
     * {@code wait_for_target}: ждать, пока в радиусе {@code radius} от
     * сущности-заклинания не окажется живая сущность (при
     * {@code excludeCaster} — кроме кастера), но не дольше {@code maxSeconds}.
     * Ловушка: «лечь и ждать, а когда подойдут — сработать». Ищет раз в
     * {@code homing.searchIntervalTicks} тиков.
     */
    static final class WaitForTarget extends Behavior {
        private final double radius;
        private final double maxSeconds;
        private final boolean excludeCaster;

        WaitForTarget(double radius, double maxSeconds, boolean excludeCaster) {
            this.radius = radius;
            this.maxSeconds = maxSeconds;
            this.excludeCaster = excludeCaster;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            int interval = entity.session().config().settings().homing().searchIntervalTicks();
            int[] left = {ticks(maxSeconds)};
            int[] untilSearch = {0};
            return e -> {
                if (--left[0] <= 0) {
                    return true;
                }
                if (--untilSearch[0] > 0) {
                    return false;
                }
                untilSearch[0] = interval;
                return !nearby(e, radius, excludeCaster).isEmpty();
            };
        }
    }

    /** Живые в радиусе от сущности-заклинания. */
    private static List<LivingEntity> nearby(SpellEntity entity, double radius, boolean excludeCaster) {
        LivingEntity caster = entity.session().caster();
        Vec3 at = entity.position();
        return entity.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && !(excludeCaster && e == caster)
                        && e.getBoundingBox().getCenter().distanceTo(at) <= radius);
    }

    /**
     * {@code aura}: пока идёт интерфикс, раз в {@code every} с применять
     * действие {@code action} ко всем живым в радиусе {@code radius} от
     * сущности-заклинания (при {@code excludeCaster} — кроме кастера).
     * Первый раз — сразу. Интерфикс не удлиняет: ставьте рядом
     * {@code wait}, {@code home} или движение.
     */
    static final class Aura extends Behavior {
        private final double radius;
        private final double every;
        private final boolean excludeCaster;
        private final Behavior action;

        Aura(double radius, double every, boolean excludeCaster, Behavior action) {
            this.radius = radius;
            this.every = every;
            this.excludeCaster = excludeCaster;
            this.action = action;
        }

        @Override
        public Kind kind() {
            return Kind.PROCESS;
        }

        @Override
        public Process start(SpellEntity entity) {
            int interval = ticks(every);
            Arcane arcane = entity.currentInterfix();
            return new Process() {
                private int untilPulse;

                @Override
                public boolean tick(SpellEntity e) {
                    if (--untilPulse <= 0) {
                        untilPulse = interval;
                        pulse(e, arcane);
                    }
                    return true;
                }

                @Override
                public boolean blocking() {
                    return false;
                }
            };
        }

        private void pulse(SpellEntity entity, Arcane arcane) {
            List<LivingEntity> found = nearby(entity, radius, excludeCaster);
            ServerLevel level = (ServerLevel) entity.level();
            if (arcane != null) {
                SpellParticles.ring(level, arcane.particles(Role.INTERFIX), entity.position(), radius);
            }
            if (found.isEmpty()) {
                return;
            }
            SpellContext context = new SpellContext(entity.session(), entity, arcane);
            context.selectStrictly(found);
            context.enter(arcane, Role.INTERFIX);
            action.act(context, false);
        }
    }
}
