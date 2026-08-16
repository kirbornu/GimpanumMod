package com.kirbornu.gimpanum.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

    /**
     * Ниже сотни он не опускается.
     *
     * <p>Высота абсолютная, а не «над землёй»: барханы Гимпанума кончаются
     * около сотого блока, и молния должна висеть над ними, а не следовать за
     * рельефом в низину.
     */
    private static final int FLOOR = 100;
    private static final int CEILING = 122;
    private static final double LIFT = 0.05;

    public PlasmaBolt(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setNoGravity(true);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.145)
                // 0.485 — четверть блока в секунду. Летающие мобы ходят по
                // FLYING_SPEED, а не по MOVEMENT_SPEED; связь линейная, вымерено.
                .add(Attributes.FLYING_SPEED, 0.485)
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
        // Раз в три-четыре секунды, с расстояния, на котором ответить нечем.
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 0.9, 60, 80, 30.0F));
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
        if (this.getY() < FLOOR) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, LIFT, 0.0));
        } else if (this.getY() > CEILING) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -LIFT, 0.0));
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        Vec3 from = this.getEyePosition();
        PlasmaProjectile bolt = new PlasmaProjectile(this.level(), this,
                new Vec3(target.getX() - from.x,
                        target.getY(0.5) - from.y,
                        target.getZ() - from.z).normalize());
        bolt.setPos(from.x, from.y, from.z);
        this.level().addFreshEntity(bolt);
        this.playSound(GimpanumSounds.BOLT_SHOOT.get(), 2.0F, 1.0F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GimpanumSounds.BOLT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GimpanumSounds.BOLT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GimpanumSounds.BOLT_DEATH.get();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    /**
     * Свет ничего не решает.
     *
     * <p>{@link net.minecraft.world.entity.monster.Monster} оценивает точку
     * появления по освещённости, и чем светлее — тем хуже. В Гимпануме вечный
     * полдень и {@code ambient_light: 1.0}, то есть предельно светло везде:
     * по этой мерке всё измерение непригодно, и ни один моб из ветки Монстра
     * не появился бы нигде и никогда. Мерку убираем — по той же причине, по
     * какой свет не участвует и в условиях появления.
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

}
