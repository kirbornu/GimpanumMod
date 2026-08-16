package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Кружение вокруг цели с наскоками — сквозь всё, что окажется между.
 *
 * <p>Обычная навигация тут не годится: она ищет путь по проходимым клеткам, а
 * призраку проходимо всё. Скорость задаётся вручную, с инерцией, чтобы он
 * подплывал, а не дёргался.
 *
 * <p>Летит не в саму жертву, а в точку на окружности вокруг неё, и точка эта
 * всё время смещается — получается вихрь, от которого не отвернуться, как от
 * Досаждателя. К самой жертве призрак ныряет, только когда пора бить.
 */
public class PhaseChaseGoal extends Goal {

    /** Радиус кружения. */
    private static final double ORBIT = 4.0;

    /** Насколько быстро смещается точка на окружности, радиан за тик. */
    private static final double SWIRL = 0.13;

    /** Ближе этого — можно бить. */
    private static final double REACH = 3.2;

    private final Mob mob;
    private final int attackInterval;
    private double angle;
    private int cooldown;

    public PhaseChaseGoal(Mob mob, int attackInterval) {
        this.mob = mob;
        this.attackInterval = attackInterval;
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
        angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        angle += SWIRL;

        boolean striking = cooldown <= 0;
        Vec3 aim = striking
                ? target.getEyePosition()
                : target.getEyePosition().add(Math.cos(angle) * ORBIT, 0.4, Math.sin(angle) * ORBIT);

        double speed = mob.getAttributeValue(Attributes.FLYING_SPEED);
        Vec3 pull = aim.subtract(mob.position()).normalize().scale(speed);
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8).add(pull.scale(0.2)));
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (cooldown > 0) {
            cooldown--;
        } else if (mob.distanceToSqr(target) <= REACH * REACH) {
            cooldown = attackInterval;
            mob.doHurtTarget(target);
        }
    }

    @Override
    public void stop() {
        cooldown = 0;
    }
}
