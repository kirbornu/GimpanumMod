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
 * <p>Время зависит от прочности блока — песок уходит за полторы секунды,
 * обсидиан за минуту. Блоки с отрицательной прочностью (коренная порода,
 * Ядро, врата) не трогаются вовсе: это не «крепко», это «нельзя».
 */
public class DevourBlocksGoal extends Goal {

    private static final int STALL = 40;
    private static final double GIVE_UP = 3.0;
    private static final double PROGRESS = 1.5;
    private static final int RANGE = 3;
    private static final int BASE_TICKS = 20;
    private static final double TICKS_PER_HARDNESS = 20.0;

    private final Mob mob;

    @Nullable
    private BlockPos chewing;
    private int progress;
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
        if (!pos.equals(chewing)) {
            clearProgress();
            chewing = pos;
            progress = 0;
        }

        Level level = mob.level();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            clearProgress();
            return;
        }

        int needed = (int) (BASE_TICKS + hardness * TICKS_PER_HARDNESS);
        progress++;
        if (progress % 8 == 0) {
            level.playSound(null, pos, state.getSoundType(level, pos, mob).getHitSound(), SoundSource.HOSTILE, 0.6F, 0.6F);
        }
        level.destroyBlockProgress(mob.getId(), pos, Math.min(9, progress * 10 / Math.max(needed, 1)));

        if (progress >= needed) {
            // Без выпадения: поглотитель не добывает, он поглощает.
            level.destroyBlock(pos, false);
            clearProgress();
            closest = Double.MAX_VALUE;
            stalled = 0;
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
        // Цель выше или ниже — значит мешает потолок или пол.
        BlockPos vertical = target.getY() > mob.getY() + 1.0 ? feet.above(2) : feet.below();
        return edible(level, vertical) ? vertical : null;
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
    }

    private void reset() {
        clearProgress();
        stalled = 0;
        closest = Double.MAX_VALUE;
    }
}
