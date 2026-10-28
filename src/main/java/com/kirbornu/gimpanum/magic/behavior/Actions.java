package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.spell.BlockEdits;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
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

    // --- движение целей ---------------------------------------------------

    /** Отправить скорость игроку сразу: без этого клиент игрока узнает о толчке с опозданием. */
    private static void sync(LivingEntity target) {
        target.hurtMarked = true;
    }

    /**
     * {@code knockback}: отбросить цели от сущности-заклинания с силой
     * {@code strength} — ванильным отбрасыванием, как от удара, с учётом
     * сопротивления отбрасыванию. Цель, стоящая на одной вертикали с
     * сущностью (обычно сам кастер), отбрасывается по взгляду кастера.
     * Основной параметр — {@code strength}.
     */
    static final class Knockback extends Behavior {
        private final double strength;

        Knockback(double strength) {
            this.strength = strength;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            double force = context.primary(strength, root);
            Vec3 origin = context.entity().position();
            for (LivingEntity target : context.targets()) {
                double dx = target.getX() - origin.x;
                double dz = target.getZ() - origin.z;
                if (dx * dx + dz * dz < 1.0E-4) {
                    dx = context.direction().x;
                    dz = context.direction().z;
                }
                // Ванильное отбрасывание толкает против переданного вектора.
                target.knockback(force, -dx, -dz);
                sync(target);
            }
        }
    }

    /**
     * {@code pull}: притянуть цели к сущности-заклинанию с силой
     * {@code strength} — тем же ванильным отбрасыванием, но к ней. Цель на
     * одной вертикали с сущностью не двигается. Основной параметр —
     * {@code strength}.
     */
    static final class Pull extends Behavior {
        private final double strength;

        Pull(double strength) {
            this.strength = strength;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            double force = context.primary(strength, root);
            Vec3 origin = context.entity().position();
            for (LivingEntity target : context.targets()) {
                double dx = target.getX() - origin.x;
                double dz = target.getZ() - origin.z;
                if (dx * dx + dz * dz < 1.0E-4) {
                    continue;
                }
                target.knockback(force, dx, dz);
                sync(target);
            }
        }
    }

    /**
     * {@code launch}: подбросить цели вверх, добавив вертикальную скорость
     * {@code strength} блоков за тик (с учётом сопротивления отбрасыванию).
     * Для понимания: 0.42 — обычный прыжок, 1.0 — примерно на пять блоков.
     * Основной параметр — {@code strength}.
     */
    static final class Launch extends Behavior {
        private final double strength;

        Launch(double strength) {
            this.strength = strength;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            double force = context.primary(strength, root);
            for (LivingEntity target : context.targets()) {
                double lift = force * (1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                if (lift <= 0) {
                    continue;
                }
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(v.x, Math.max(v.y, 0) + lift, v.z);
                target.hasImpulse = true;
                sync(target);
            }
        }
    }

    // --- состояние целей --------------------------------------------------

    /**
     * {@code freeze}: заморозить, как в порошковом снегу, на {@code seconds} с:
     * замедление и урон от холода по ванильным правилам, затем оттаивание
     * за 3,5 с. Кто не мёрзнет (зимогор, кожаная броня), тот не мёрзнет и
     * здесь. Длительное.
     */
    static final class Freeze extends Behavior {
        /** Вне снега игра снимает по два тика заморозки за тик. */
        private static final int THAW_PER_TICK = 2;

        private final double seconds;

        Freeze(double seconds) {
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
            long ticks = ticks(context.duration(seconds, root));
            for (LivingEntity target : context.targets()) {
                if (!target.canFreeze()) {
                    continue;
                }
                int frozen = (int) Math.min(Integer.MAX_VALUE / 2,
                        target.getTicksRequiredToFreeze() + THAW_PER_TICK * ticks);
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), frozen));
            }
        }
    }

    /**
     * {@code feed}: накормить игроков — {@code food} к уровню еды и
     * {@code saturation} к насыщенности, не выше ванильных пределов.
     * Мобов не кормит. Основной параметр — {@code food}.
     */
    static final class Feed extends Behavior {
        private final int food;
        private final double saturation;

        Feed(int food, double saturation) {
            this.food = food;
            this.saturation = saturation;
        }

        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            int amount = (int) Math.round(context.primary(food, root));
            for (LivingEntity target : context.targets()) {
                if (target instanceof Player player) {
                    FoodData data = player.getFoodData();
                    data.setFoodLevel(Mth.clamp(data.getFoodLevel() + amount, 0, 20));
                    // Насыщенность в игре не бывает больше уровня еды.
                    data.setSaturation((float) Mth.clamp(data.getSaturationLevel() + saturation, 0,
                            data.getFoodLevel()));
                }
            }
        }
    }

    /**
     * {@code absorb}: {@code amount} единиц поглощения (золотые сердца) на
     * {@code seconds} с — через ванильный эффект «Поглощение», поэтому они
     * исчезают вместе с ним. Больше, чем уже есть, не убавляет. Основной
     * параметр — {@code amount}. Длительное.
     */
    static final class Absorb extends Behavior {
        private final double amount;
        private final double seconds;

        Absorb(double amount, double seconds) {
            this.amount = amount;
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
            float value = (float) context.primary(amount, root);
            int duration = (int) ticks(context.duration(seconds, root));
            // Уровень эффекта задаёт потолок поглощения: по 4 единицы за уровень.
            int amplifier = Mth.clamp((int) Math.ceil(value / 4.0) - 1, 0, 255);
            LivingEntity caster = context.session().caster();
            for (LivingEntity target : context.targets()) {
                float before = target.getAbsorptionAmount();
                if (target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier), caster)) {
                    context.session().noteEffect(target, MobEffects.ABSORPTION);
                }
                target.setAbsorptionAmount(Math.max(before, value));
            }
        }
    }

    /** {@code extinguish}: потушить цели — и только, эффекты не трогает. */
    static final class Extinguish extends Behavior {
        @Override
        public Kind kind() {
            return Kind.ACTION;
        }

        @Override
        public void act(SpellContext context, boolean root) {
            for (LivingEntity target : context.targets()) {
                target.clearFire();
            }
        }
    }
}
