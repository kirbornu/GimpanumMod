package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Без жертвы — обратно вниз.
 *
 * <p>Призрак кометы обитает у самого дна лабиринта, и подниматься к барханам
 * ему незачем. Без этой цели он, погонявшись за кем-нибудь наверху, так бы
 * там и остался.
 */
public class SinkToDepthsGoal extends Goal {

    private final Mob mob;
    private final int home;
    private final double drift;

    public SinkToDepthsGoal(Mob mob, int home, double drift) {
        this.mob = mob;
        this.home = home;
        this.drift = drift;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        double dy = mob.getY() - home;
        double vertical = Math.abs(dy) < 2.0 ? 0.0 : (dy > 0 ? -drift : drift);
        // Лёгкое блуждание вбок, чтобы он не висел свечкой на одном месте.
        double wander = (mob.getRandom().nextDouble() - 0.5) * drift * 0.6;
        mob.setDeltaMovement(mob.getDeltaMovement().scale(0.9)
                .add(wander, vertical, wander));
    }
}
