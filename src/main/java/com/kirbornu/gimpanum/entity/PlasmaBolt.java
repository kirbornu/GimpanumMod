package com.kirbornu.gimpanum.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Плазменная молния — то, что висит над барханами и стреляет.
 *
 * <p>Летает крайне медленно и почти не приближается: вся опасность в
 * дальности. Держится высоко над песком сама — {@link #aiStep()} правит
 * высоту, — потому что смысл её в том, чтобы бить оттуда, куда не достать.
 *
 * <p>Модель скелета взята ради облика; наследоваться от скелета нельзя: тот
 * ходит по земле и горит на солнце, а тут вечный полдень.
 */
public class PlasmaBolt extends Monster implements RangedAttackMob {

    private static final int MIN_ALTITUDE = 10;
    private static final int MAX_ALTITUDE = 26;
    private static final double LIFT = 0.04;

    public PlasmaBolt(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setNoGravity(true);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.FLYING_SPEED, 0.12)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 0.9, 50, 30.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 40.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)
                .setUnseenMemoryTicks(400));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getBlockX(), this.getBlockZ());
        double altitude = this.getY() - ground;
        if (altitude < MIN_ALTITUDE) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, LIFT, 0.0));
        } else if (altitude > MAX_ALTITUDE) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -LIFT, 0.0));
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        Vec3 from = this.getEyePosition();
        SmallFireball bolt = new SmallFireball(this.level(), this,
                new Vec3(target.getX() - from.x,
                        target.getY(0.5) - from.y,
                        target.getZ() - from.z).normalize());
        bolt.setPos(from.x, from.y, from.z);
        this.level().addFreshEntity(bolt);
        this.playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1.5F, 1.6F);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }
}
