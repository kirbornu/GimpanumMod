package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.spell.BlockEdits;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;

/** Поведения-действия: что заклинание делает с целями и блоками. */
final class Actions {

    private Actions() {
    }

    /** Секунды в тики; отрицательное — «навсегда», ноль тиков. */
    private static long ticks(double seconds) {
        return seconds <= 0 ? 0 : Math.round(seconds * 20);
    }

    /** {@code damage}: магический урон {@code amount}. Основной параметр — {@code amount}. */
    static final class Damage extends Behavior {
        private final double amount;

        Damage(double amount) {
            this.amount = amount;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            LivingEntity caster = context.session().caster();
            float damage = (float) context.primary(amount, root);
            for (LivingEntity target : context.targets()) {
                target.hurt(context.level().damageSources().indirectMagic(context.entity(),
                        caster != null && !caster.isRemoved() ? caster : null), damage);
            }
        }
    }

    /** {@code ignite}: поджечь на {@code seconds} с. Длительное. */
    static final class Ignite extends Behavior {
        private final double seconds;

        Ignite(double seconds) {
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public boolean lasting() {
            return true;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            float duration = (float) context.duration(seconds, root);
            for (LivingEntity target : context.targets()) {
                target.igniteForSeconds(duration);
                context.session().noteIgnited(target);
            }
        }
    }

    /** {@code heal}: вылечить на {@code amount}. Основной параметр — {@code amount}. */
    static final class Heal extends Behavior {
        private final double amount;

        Heal(double amount) {
            this.amount = amount;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            float health = (float) context.primary(amount, root);
            for (LivingEntity target : context.targets()) {
                target.heal(health);
            }
        }
    }

    /** {@code effect}: эффект {@code effect} уровня {@code amplifier} на {@code seconds} с. Длительное. */
    static final class Effect extends Behavior {
        private final Holder<MobEffect> effect;
        private final double seconds;
        private final int amplifier;

        Effect(Holder<MobEffect> effect, double seconds, int amplifier) {
            this.effect = effect;
            this.seconds = seconds;
            this.amplifier = amplifier;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public boolean lasting() {
            return true;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            int duration = (int) ticks(context.duration(seconds, root));
            LivingEntity caster = context.session().caster();
            for (LivingEntity target : context.targets()) {
                if (target.addEffect(new MobEffectInstance(effect, duration, amplifier), caster)) {
                    context.session().noteEffect(target, effect);
                }
            }
        }
    }

    /**
     * {@code cleanse}: снять с целей все эффекты и потушить — кроме того, что
     * наложило и подожгло это же заклинание.
     */
    static final class Cleanse extends Behavior {
        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            for (LivingEntity target : context.targets()) {
                for (MobEffectInstance instance : new ArrayList<>(target.getActiveEffects())) {
                    if (!context.session().appliedEffect(target, instance.getEffect())) {
                        target.removeEffect(instance.getEffect());
                    }
                }
                if (target.isOnFire() && !context.session().ignited(target)) {
                    target.clearFire();
                }
            }
        }
    }

    /**
     * {@code echo_root}: копия на месте сущности-заклинания, которая
     * {@code duration} с раз в {@code interval} с применяет только корень.
     */
    static final class EchoRoot extends Behavior {
        private final double interval;
        private final double duration;

        EchoRoot(double interval, double duration) {
            this.interval = interval;
            this.duration = duration;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            int times = (int) Math.ceil(duration / interval - 1.0E-9);
            context.entity().spawnEcho(context.root(), (int) Math.max(1, ticks(interval)), times);
        }
    }

    /**
     * {@code water}: заполнить выделенные блоки водой. Воздух становится
     * источником, блоки, которые умеют, обводняются. {@code seconds} — сколько
     * вода продержится; отрицательное — навсегда. В измерениях, где вода
     * испаряется (Незер), она испаряется, как из ведра.
     */
    static final class Water extends Behavior {
        private final double seconds;

        Water(double seconds) {
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public boolean lasting() {
            return seconds > 0;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            ServerLevel level = context.level();
            long ticks = ticks(seconds > 0 ? context.duration(seconds, root) : seconds);
            FluidStack water = new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME);
            boolean vaporized = false;
            for (BlockPos pos : context.blockTargets()) {
                BlockState state = level.getBlockState(pos);
                boolean air = state.isAir();
                boolean waterloggable = state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && !state.getValue(BlockStateProperties.WATERLOGGED);
                if (!air && !waterloggable) {
                    continue;
                }
                if (Fluids.WATER.getFluidType().isVaporizedOnPlacement(level, pos, water)) {
                    // Шипение и пар — один раз на всё действие, а не над каждым блоком.
                    if (!vaporized && BlockEdits.mayEdit(context.session(), pos)) {
                        Fluids.WATER.getFluidType().onVaporize(null, level, pos, water);
                        vaporized = true;
                    }
                    continue;
                }
                if (air) {
                    BlockEdits.place(context.session(), pos, Blocks.WATER.defaultBlockState(), state, ticks);
                } else {
                    BlockState wet = state.setValue(BlockStateProperties.WATERLOGGED, true);
                    if (BlockEdits.place(context.session(), pos, wet, state, ticks)) {
                        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                    }
                }
            }
        }
    }

    /**
     * {@code light}: невидимый блок света яркостью {@code level} в выделенных
     * блоках, только в воздухе, на {@code seconds} с. Основной параметр —
     * {@code level}.
     */
    static final class Light extends Behavior {
        private final int brightness;
        private final double seconds;

        Light(int brightness, double seconds) {
            this.brightness = brightness;
            this.seconds = seconds;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public boolean lasting() {
            return seconds > 0;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            int level = Mth.clamp((int) Math.round(context.primary(brightness, root)), 0, LightBlock.MAX_LEVEL);
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
            long ticks = ticks(seconds > 0 ? context.duration(seconds, root) : seconds);
            for (BlockPos pos : context.blockTargets()) {
                BlockState state = context.level().getBlockState(pos);
                if (state.isAir()) {
                    BlockEdits.place(context.session(), pos, light, state, ticks);
                }
            }
        }
    }
}
