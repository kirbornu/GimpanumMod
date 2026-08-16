package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Ближний бой со своим темпом.
 *
 * <p>Ванильный интервал жёстко зашит в двадцать тиков, а перезарядка хранится
 * в приватном поле, поэтому проще вести свой счётчик, чем пытаться подменить
 * чужой.
 */
public class PacedMeleeAttackGoal extends MeleeAttackGoal {

    private final int interval;
    private int cooldown;

    public PacedMeleeAttackGoal(PathfinderMob mob, double speedModifier, int interval) {
        super(mob, speedModifier, true);
        this.interval = interval;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (this.mob.isWithinMeleeAttackRange(target) && this.mob.getSensing().hasLineOfSight(target)) {
            cooldown = interval;
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    @Override
    public void stop() {
        super.stop();
        cooldown = 0;
    }
}
