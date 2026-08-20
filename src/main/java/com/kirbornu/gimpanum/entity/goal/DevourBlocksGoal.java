package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Прогрызание пути к цели.
 *
 * <p>Включается не сразу: сперва моб честно пытается дойти. Только если он
 * {@value #STALL} тиков не приблизился к жертве, он начинает есть то, что
 * стоит на пути. Иначе поглотитель грыз бы стены, имея открытую дверь рядом.
 *
 * <p>Грызёт не по блоку, а сразу шаром радиусом {@value #BITE}: поглотитель
 * четыре блока в ширину, и однин выеденный кубик ему бесполезен. Время
 * считается по самому крепкому блоку в шаре — иначе обсидиановую стену можно
 * было бы обмануть, спрятав за ней песок.
 *
 * <p>Ест почти мгновенно: песок исчезает за тик, обсидиан — за треть
 * секунды. Не преграда, а задержка на один вдох. Блоки с отрицательной
 * прочностью (коренная порода, Ядро, врата) не трогаются вовсе: это не
 * «крепко», это «нельзя».
 */
public class DevourBlocksGoal extends Goal {

    private static final int STALL = 40;
    private static final double GIVE_UP = 3.0;
    private static final double PROGRESS = 1.5;
    private static final int RANGE = 3;

    /** Радиус выедаемой полости. */
    private static final int BITE = 3;
    // Сотая доля от прежних тринадцати: стена перестала быть стеной.
    private static final double BASE_TICKS = 0.13;
    private static final double TICKS_PER_HARDNESS = 0.13;

    private final Mob mob;

    @Nullable
    private BlockPos chewing;
    private int progress;
    private int needed;
    private int stalled;
    private double closest = Double.MAX_VALUE;

    public DevourBlocksGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.distanceToSqr(target) < GIVE_UP * GIVE_UP) {
            reset();
            return false;
        }
        double distance = mob.distanceToSqr(target);
        // Приблизился хотя бы на полтора блока — значит путь есть, грызть незачем.
        if (distance + PROGRESS * PROGRESS < closest) {
            closest = distance;
            stalled = 0;
        } else {
            stalled++;
        }
        return stalled >= STALL;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(target) >= GIVE_UP * GIVE_UP;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        BlockPos pos = pick(target);
        if (pos == null) {
            clearProgress();
            return;
        }
        Level level = mob.level();
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            clearProgress();
            return;
        }

        if (!pos.equals(chewing)) {
            clearProgress();
            chewing = pos;
            progress = 0;
            // Обход шара — 343 клетки; считаем его один раз на укус, а не
            // каждый тик. И не меньше тика: мгновенное — это всё-таки один
            // тик, а не ноль.
            needed = Math.max(1, (int) Math.ceil(BASE_TICKS + hardestAround(level, pos) * TICKS_PER_HARDNESS));
            // Звук на начало укуса, а не раз в восемь тиков: укус столько
            // уже и не длится, отбивать больше нечего.
            level.playSound(null, pos, state.getSoundType(level, pos, mob).getHitSound(), SoundSource.HOSTILE, 0.6F, 0.6F);
        }

        progress++;
        level.destroyBlockProgress(mob.getId(), pos, Math.min(9, progress * 10 / Math.max(needed, 1)));

        if (progress >= needed) {
            bite(level, pos);
            clearProgress();
            closest = Double.MAX_VALUE;
            stalled = 0;
        }
    }

    /** Прочность самого крепкого блока в шаре — по нему и считается время. */
    private float hardestAround(Level level, BlockPos centre) {
        float hardest = 0.0F;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BITE, -BITE, -BITE), centre.offset(BITE, BITE, BITE))) {
            if (centre.distSqr(pos) > (double) BITE * BITE) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (!state.isAir() && hardness > hardest) {
                hardest = hardness;
            }
        }
        return hardest;
    }

    /** Выедает шар. Неразрушимое остаётся стоять — вокруг него и обгрызает. */
    private void bite(Level level, BlockPos centre) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BITE, -BITE, -BITE), centre.offset(BITE, BITE, BITE))) {
            if (centre.distSqr(pos) > (double) BITE * BITE) {
                continue;
            }
            if (edible(level, pos)) {
                // Без выпадения: поглотитель не добывает, он поглощает.
                level.destroyBlock(pos.immutable(), false);
            }
        }
    }

    @Override
    public void stop() {
        clearProgress();
    }

    /** Что именно грызть: первая преграда по направлению к цели. */
    @Nullable
    private BlockPos pick(LivingEntity target) {
        Level level = mob.level();
        Vec3 towards = target.position().subtract(mob.position());
        Direction facing = Direction.getNearest(towards.x, 0.0, towards.z);
        BlockPos feet = mob.blockPosition();

        // Сначала вперёд: на уровне головы, потом на уровне ног.
        for (int step = 1; step <= RANGE; step++) {
            for (int height : new int[]{1, 0}) {
                BlockPos candidate = feet.above(height).relative(facing, step);
                if (edible(level, candidate)) {
                    return candidate;
                }
            }
        }
        // Вертикаль трогаем, только если цель и правда выше или ниже. Иначе
        // поглотитель на ровном месте начинал копать яму прямо под собой.
        if (target.getY() > mob.getY() + 2.0) {
            BlockPos above = feet.above(2);
            return edible(level, above) ? above : null;
        }
        if (target.getY() < mob.getY() - 2.0) {
            BlockPos below = feet.below();
            return edible(level, below) ? below : null;
        }
        return null;
    }

    private boolean edible(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private void clearProgress() {
        if (chewing != null) {
            mob.level().destroyBlockProgress(mob.getId(), chewing, -1);
            chewing = null;
        }
        progress = 0;
        needed = 0;
    }

    private void reset() {
        clearProgress();
        stalled = 0;
        closest = Double.MAX_VALUE;
    }
}
