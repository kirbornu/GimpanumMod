package com.kirbornu.gimpanum.entity.goal;

import com.kirbornu.gimpanum.entity.DuneCaptain;
import com.kirbornu.gimpanum.entity.DuneWalker;
import com.kirbornu.gimpanum.entity.MobStats;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Строй: толпа облаком вокруг капитана.
 *
 * <p>Не места по номерам, а два простых правила, из которых облако
 * складывается само. Первое — держаться не дальше радиуса от капитана:
 * отставший идёт к нему, пока не окажется внутри. Второе — не стоять вплотную
 * к соседу: каждый, кто ближе шага строя, отталкивает, и сильнее, чем ближе.
 * Капитан тоже сосед — поэтому он оказывается в середине толпы, а не
 * погребён под ней, и облако выходит ровным со всех сторон.
 *
 * <p>Радиус растёт с отрядом так, чтобы на каждого приходилось место в шаг
 * строя: площадь круга равна числу солдат, умноженному на квадрат шага.
 *
 * <p>Думает не каждый тик, а раз в полсекунды с разбросом: иначе сотня
 * солдат искала бы путь в один и тот же тик. Путь при этом короткий — до
 * точки в нескольких шагах, — и обходится дёшево.
 */
public class FollowCaptainGoal extends Goal {

    /** Раз в столько тиков — пересмотр своего места в толпе. */
    private static final int THINK = 10;

    /** Если сдвинуться надо меньше, чем на столько, — солдат уже на месте. */
    private static final double SETTLED = 0.5;

    /** Настолько отставший за пределами облака прибавляет шагу, чтобы догнать. */
    private static final double BEHIND = 3.0;

    private static final double CATCH_UP = 1.25;

    private final DuneWalker soldier;
    private int think;

    public FollowCaptainGoal(DuneWalker soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return soldier.getTarget() == null && soldier.leader() != null;
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
    public void start() {
        think = 0;
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--think > 0) {
            return;
        }
        think = THINK + soldier.getRandom().nextInt(THINK / 2 + 1);
        DuneCaptain leader = soldier.leader();
        if (leader == null) {
            return;
        }

        double spacing = MobStats.of("dune_walker").number("formation_spacing_blocks");
        double radius = spacing * Math.sqrt((leader.squadCount() + 1) / Math.PI);

        // Центр облака — там, где капитан будет к следующему пересмотру: иначе
        // толпа всё время плелась бы у него за спиной, а не вокруг.
        double centreX = leader.getX() + leader.getDeltaMovement().x * THINK;
        double centreZ = leader.getZ() + leader.getDeltaMovement().z * THINK;
        double dx = centreX - soldier.getX();
        double dz = centreZ - soldier.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double stepX = 0.0;
        double stepZ = 0.0;
        if (distance > radius) {
            stepX += dx / distance * (distance - radius);
            stepZ += dz / distance * (distance - radius);
        }
        for (DuneWalker other : soldier.level().getEntitiesOfClass(DuneWalker.class,
                soldier.getBoundingBox().inflate(spacing, 1.0, spacing), other -> other != soldier)) {
            double ox = soldier.getX() - other.getX();
            double oz = soldier.getZ() - other.getZ();
            double gap = Math.sqrt(ox * ox + oz * oz);
            if (gap >= spacing) {
                continue;
            }
            if (gap < 1.0E-3) {
                // Стоят в одной точке — расходиться куда угодно, лишь бы не на месте.
                double angle = soldier.getRandom().nextDouble() * Math.PI * 2.0;
                ox = Math.cos(angle);
                oz = Math.sin(angle);
                gap = 1.0;
            }
            stepX += ox / gap * (spacing - gap);
            stepZ += oz / gap * (spacing - gap);
        }

        if (stepX * stepX + stepZ * stepZ < SETTLED * SETTLED) {
            soldier.getNavigation().stop();
            return;
        }
        double speed = distance > radius + BEHIND ? CATCH_UP : 1.0;
        soldier.getNavigation().moveTo(soldier.getX() + stepX, leader.getY(), soldier.getZ() + stepZ, speed);
    }
}
