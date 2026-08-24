package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Погоня по прямой, без путеискания.
 *
 * <p>Прежде поглотитель гнался через навигацию и {@code FlyingMoveControl}, и
 * это оказалось тупиком: скорость упиралась в потолок около трёх блоков в
 * секунду и дальше не отзывалась на атрибут вовсе — вымерено, значения 2, 6 и
 * 12 давали одно и то же. Управление полётом ведёт моба от узла к узлу пути,
 * тормозя у каждого, и никакая величина в атрибуте этого не отменяет.
 *
 * <p>Поэтому скорость задаётся напрямую, как у Призрака: каждый тик к
 * скорости примешивается тяга в сторону жертвы. {@code FLYING_SPEED} здесь —
 * блоки за тик, и число в нём наконец означает ровно то, что означает.
 *
 * <p>Никаких обходов и рысканий: поглотитель — червь, он идёт в жертву по
 * прямой сквозь всё, что между ними, а породу перед мордой убирает
 * {@link DevourBlocksGoal}.
 */
public class BoreChaseGoal extends Goal {

    /** Ближе этого — можно бить. */
    private static final double REACH = 4.0;

    /**
     * Доля новой тяги в скорости за тик.
     *
     * <p>Пятая часть: при мгновенной подмене моб дёргался бы на каждом
     * повороте жертвы, при меньшей — входил бы в поворот по широкой дуге и
     * промахивался мимо прогрызенного туннеля.
     */
    private static final double BLEND = 0.2;

    private final Mob mob;
    private final int attackInterval;

    private int cooldown;

    public BoreChaseGoal(Mob mob, int attackInterval) {
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
        double speed = mob.getAttributeValue(Attributes.FLYING_SPEED);
        Vec3 pull = target.getEyePosition().subtract(mob.getEyePosition());
        if (pull.lengthSqr() > 1.0E-6) {
            pull = pull.normalize().scale(speed);
            mob.setDeltaMovement(mob.getDeltaMovement().scale(1.0 - BLEND).add(pull.scale(BLEND)));
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (cooldown > 0) {
            cooldown--;
        } else if (mob.distanceToSqr(target) <= REACH * REACH) {
            cooldown = attackInterval;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
