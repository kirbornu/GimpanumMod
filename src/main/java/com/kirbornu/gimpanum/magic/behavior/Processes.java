package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.config.MagicSettings;
import com.kirbornu.gimpanum.magic.spell.BlockEdits;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.spell.TempBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;

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
}
