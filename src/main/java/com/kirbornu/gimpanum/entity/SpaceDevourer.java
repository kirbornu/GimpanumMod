package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.AvoidLightGoal;
import com.kirbornu.gimpanum.entity.goal.DevourBlocksGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Поглотитель космоса — быстрый, лазающий и прогрызающий.
 *
 * <p>Паук по повадкам и по модели, но крупнее и совершенно чёрный. Догнать
 * его нельзя, спрятаться за стеной — тоже: стену он съест, вопрос только во
 * времени (см. {@link DevourBlocksGoal}).
 *
 * <p>Единственная защита — свет. Яркое место обращает поглотителя в бегство,
 * и цель преследования при этом отменяется: {@link AvoidLightGoal} стоит выше
 * и занимает тот же флаг движения.
 */
public class SpaceDevourer extends Monster {

    /** Сколько тиков поглотитель помнит цель, потерянную из виду. */
    private static final int MEMORY = 600;

    public SpaceDevourer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidLightGoal(this, 1.3));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(4, new DevourBlocksGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Замечает по взгляду, но помнит полминуты: иначе стена, за которой
        // спрятался игрок, сразу переставала бы иметь смысл.
        this.targetSelector.addGoal(1, (HurtByTargetGoal) new HurtByTargetGoal(this)
                .setUnseenMemoryTicks(MEMORY));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true)
                .setUnseenMemoryTicks(MEMORY));
    }

    /** Лазает по отвесному, как паук: упёрся — значит полез. */
    @Override
    public boolean onClimbable() {
        return this.horizontalCollision;
    }
}
