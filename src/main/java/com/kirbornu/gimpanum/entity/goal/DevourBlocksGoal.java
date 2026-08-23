package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.core.BlockPos;
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
 * Прогрызание пути к цели по прямой.
 *
 * <p>Поглотитель летает и не ищет обходов: он смотрит на жертву и проедает
 * то, что стоит между ними. Раньше цель включалась, только когда моб сорок
 * тиков не мог приблизиться, — это имело смысл для ходока, которому стоило
 * сперва поискать открытую дверь. Летающему искать нечего: если на луче к
 * жертве есть камень, значит камень и мешает, и ждать сорок тиков не за чем.
 *
 * <p>Грызёт не по блоку, а сразу шаром радиусом {@value #BITE}: поглотитель
 * четыре блока в ширину, и один выеденный кубик ему бесполезен. Время
 * считается по самому крепкому блоку в шаре — иначе обсидиановую стену можно
 * было бы обмануть, спрятав за ней песок.
 *
 * <p>Ест почти мгновенно: песок исчезает за тик, обсидиан — за треть
 * секунды. Не преграда, а задержка на один вдох. Блоки с отрицательной
 * прочностью (коренная порода, Ядро, врата) не трогаются вовсе: это не
 * «крепко», это «нельзя».
 */
public class DevourBlocksGoal extends Goal {

    /** Ближе этого грызть незачем — жертва уже на расстоянии удара. */
    private static final double GIVE_UP = 3.0;

    /** Насколько далеко вперёд смотреть по лучу к жертве. */
    private static final double REACH = 6.0;

    /** Шаг выборки по лучу: меньше половины блока, чтобы не проскочить угол. */
    private static final double STEP = 0.4;

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

    public DevourBlocksGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.distanceToSqr(target) >= GIVE_UP * GIVE_UP;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
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

    /**
     * Первая преграда на луче от глаз поглотителя к глазам жертвы.
     *
     * <p>Именно луч, а не «блок по направлению взгляда»: жертва бывает выше и
     * ниже, и червю всё равно, куда рыть. Выборка идёт с шагом меньше
     * половины блока, иначе луч наискось проскакивал бы сквозь угол между
     * двумя блоками и стена считалась бы пройденной.
     */
    @Nullable
    private BlockPos pick(LivingEntity target) {
        Level level = mob.level();
        Vec3 from = mob.getEyePosition();
        Vec3 towards = target.getEyePosition().subtract(from);
        double length = towards.length();
        if (length < 1.0E-4) {
            return null;
        }
        Vec3 step = towards.scale(STEP / length);
        // Ближе половины ширины тела смотреть нечего: там сам моб.
        double limit = Math.min(REACH, length);

        Vec3 point = from;
        for (double travelled = 0.0; travelled <= limit; travelled += STEP) {
            BlockPos candidate = BlockPos.containing(point);
            if (edible(level, candidate)) {
                return candidate;
            }
            point = point.add(step);
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
}
