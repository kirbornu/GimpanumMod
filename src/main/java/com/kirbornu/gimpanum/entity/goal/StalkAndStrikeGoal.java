package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Неотвязное преследование с обходом сбоку.
 *
 * <p>Ванильная цель ближнего боя не годится для крупного моба: она раз в
 * секунду требует построенный путь, а проход шириной в четыре блока находится
 * далеко не всегда — отсюда и вид «стоит рядом и ничего не делает». Здесь
 * непроходимость не повод останавливаться: если путь не построился, моб идёт
 * напрямик, а что окажется на дороге, доест соседняя цель.
 *
 * <p>И идёт он не в саму жертву, а в точку сбоку от неё, меняя сторону каждые
 * пару секунд. Получается рыскание вокруг: он всё время в движении и всё время
 * пробует зайти с другого бока.
 */
public class StalkAndStrikeGoal extends Goal {

    /** На сколько уводить точку прицела вбок от жертвы. */
    private static final double WEAVE = 2.5;

    /**
     * Ближе этого — рыскать, дальше — идти напрямик.
     *
     * <p>Рыскание нужно у самой жертвы, чтобы моб не замирал между ударами.
     * На подходе оно только отнимает скорость: путь вбок длиннее прямого.
     */
    private static final double WEAVE_RANGE = 8.0;

    private final PathfinderMob mob;
    private final double speed;
    private final int interval;

    private int cooldown;
    private int repath;
    private int weaveTicks;
    private int weaveSide = 1;

    public StalkAndStrikeGoal(PathfinderMob mob, double speed, int interval) {
        this.mob = mob;
        this.speed = speed;
        this.interval = interval;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
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
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.getNavigation().stop();
        cooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--weaveTicks <= 0) {
            weaveSide = -weaveSide;
            weaveTicks = 20 + mob.getRandom().nextInt(30);
        }

        Vec3 towards = target.position().subtract(mob.position());
        Vec3 sideways = new Vec3(-towards.z, 0.0, towards.x);
        boolean close = towards.lengthSqr() < WEAVE_RANGE * WEAVE_RANGE;
        Vec3 aim = !close || sideways.lengthSqr() < 1.0E-4
                ? target.position()
                : target.position().add(sideways.normalize().scale(WEAVE * weaveSide));

        if (--repath <= 0) {
            repath = 5;
            if (!mob.getNavigation().moveTo(aim.x, aim.y, aim.z, speed)) {
                // Пути нет — значит пойдём без него. Стена на дороге — забота
                // соседней цели, она её съест.
                mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, speed);
            }
        }

        if (cooldown > 0) {
            cooldown--;
        } else if (mob.isWithinMeleeAttackRange(target)) {
            cooldown = interval;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
