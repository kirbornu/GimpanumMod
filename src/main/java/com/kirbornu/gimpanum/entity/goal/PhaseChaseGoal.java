package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Полёт напрямик к цели — сквозь всё, что окажется между.
 *
 * <p>Обычная навигация тут не годится: она ищет путь по проходимым клеткам, а
 * призраку проходимо всё. Поэтому скорость задаётся вручную, с инерцией, чтобы
 * он подплывал, а не дёргался.
 */
public class PhaseChaseGoal extends Goal {

    private static final double SPEED = 0.22;
    /** С запасом: цель крупная, а призрак целится в её глаза, а не в ноги. */
    private static final double REACH = 3.2;
    private static final int ATTACK_INTERVAL = 20;

    private final Mob mob;
    private int cooldown;

    public PhaseChaseGoal(Mob mob) {
        this.mob = mob;
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
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        Vec3 pull = target.getEyePosition().subtract(mob.position()).normalize().scale(SPEED);
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.8).add(pull.scale(0.2)));
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (cooldown > 0) {
            cooldown--;
        } else if (mob.distanceToSqr(target) <= REACH * REACH) {
            cooldown = ATTACK_INTERVAL;
            mob.doHurtTarget(target);
        }
    }

    @Override
    public void stop() {
        cooldown = 0;
    }
}
