package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Непрерывное бурение к цели.
 *
 * <p>Прежняя редакция работала рывками, и это чувствовалось именно так, как и
 * выглядело: поглотитель упирался в стену, находил <i>один</i> блок,
 * выгрызал вокруг него полость в три блока, пролетал эти три блока и упирался
 * снова. Между рывками он казался растерянным, а не страшным.
 *
 * <p>Хуже того, преграду искал луч из глаз в глаза жертвы. У моба четыре
 * блока в ширину, и луч то и дело проходил в щель, тогда как туша стояла в
 * стену: искать было «нечего», и он честно висел на месте.
 *
 * <p>Теперь полость выедается <b>каждый тик</b> и не вокруг найденного блока,
 * а прямо перед мордой — сфера радиусом {@value #BITE} на {@link #lead}
 * блоков впереди середины тела. Пока впереди есть камень, он исчезает без
 * пауз, и поглотитель идёт сквозь породу с той же скоростью, с какой летел бы
 * в пустоте. Останавливает его только по-настоящему крепкое: время укуса
 * считается по самому твёрдому блоку в сфере, и даже обсидиан — это треть
 * секунды, а не преграда.
 *
 * <p>Блоки с отрицательной прочностью (коренная порода, Ядро, врата) не
 * трогаются вовсе: это не «крепко», это «нельзя», и вокруг них он обгрызает.
 */
public class DevourBlocksGoal extends Goal {

    /** Ближе этого грызть незачем — жертва уже на расстоянии удара. */
    private static final double GIVE_UP = 3.0;

    /** Радиус выедаемой полости. */
    private static final int BITE = 3;

    /**
     * Тиков на единицу прочности.
     *
     * <p>Камень исчезает за тик, обсидиан за семь. Это не преграда, а
     * запинка — ровно настолько, чтобы разница между песком и обсидианом была
     * заметна на слух.
     */
    private static final double TICKS_PER_HARDNESS = 0.13;

    /** Не чаще, чем раз в столько тиков, отбивать звук укуса. */
    private static final int SOUND_INTERVAL = 5;

    private final Mob mob;

    private int progress;
    private int needed;
    private int soundCooldown;

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
    public void stop() {
        progress = 0;
        needed = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        Level level = mob.level();
        BlockPos centre = BlockPos.containing(mouth(target));

        // Один обход сферы на тик: заодно и что грызть, и насколько крепкое.
        List<BlockPos> mouthful = new ArrayList<>();
        float hardest = 0.0F;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BITE, -BITE, -BITE),
                centre.offset(BITE, BITE, BITE))) {
            if (centre.distSqr(pos) > (double) BITE * BITE) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0.0F) {
                // Неразрушимое не считается и во время укуса: иначе врата
                // рядом со стеной делали бы стену вечной.
                continue;
            }
            mouthful.add(pos.immutable());
            if (hardness > hardest) {
                hardest = hardness;
            }
        }

        if (soundCooldown > 0) {
            soundCooldown--;
        }
        if (mouthful.isEmpty()) {
            // Впереди пусто — летим дальше, отсчёт укуса начинается заново.
            progress = 0;
            needed = 0;
            return;
        }

        if (needed <= 0) {
            needed = Math.max(1, (int) Math.ceil(hardest * TICKS_PER_HARDNESS));
        }
        if (++progress < needed) {
            return;
        }
        progress = 0;
        needed = 0;

        BlockState sample = level.getBlockState(mouthful.get(0));
        if (soundCooldown <= 0) {
            level.playSound(null, centre, sample.getSoundType(level, mouthful.get(0), mob).getHitSound(),
                    SoundSource.HOSTILE, 0.7F, 0.6F);
            soundCooldown = SOUND_INTERVAL;
        }
        for (BlockPos pos : mouthful) {
            // Без выпадения: поглотитель не добывает, он поглощает.
            level.destroyBlock(pos, false);
        }
    }

    /**
     * Куда приходится пасть: перед мордой, а не в центре тела.
     *
     * <p>Отступ считается от габарита, а не числом: сфера обязана захватывать
     * и то место, куда моб вот-вот войдёт, иначе он упирался бы в край
     * собственной полости. Направление берётся к глазам жертвы — червю
     * одинаково всё равно, рыть вверх или вниз.
     */
    private Vec3 mouth(LivingEntity target) {
        Vec3 from = mob.getEyePosition();
        Vec3 towards = target.getEyePosition().subtract(from);
        double length = towards.length();
        if (length < 1.0E-4) {
            return from;
        }
        return from.add(towards.scale(lead() / length));
    }

    private double lead() {
        return mob.getBbWidth() / 2.0 + 1.5;
    }
}
