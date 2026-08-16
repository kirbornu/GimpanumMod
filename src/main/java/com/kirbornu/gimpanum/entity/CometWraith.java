package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.PhaseChaseGoal;
import com.kirbornu.gimpanum.entity.goal.SinkToDepthsGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Призрак кометы — то, что живёт у самого дна лабиринта.
 *
 * <p>Наследуемся от Аллая только ради модели: она уже есть в игре и уже
 * отрисовывается. Мозг Аллая при этом заглушен — {@link #customServerAiStep()}
 * пуст, — а поведение задано обычными целями, как у любого моба постарше.
 *
 * <p>Стен для него не существует ни в каком смысле: он видит игрока сквозь
 * породу за {@value #DETECTION} блоков и сквозь неё же летит. Прятаться от
 * него бесполезно, можно только уйти.
 */
public class CometWraith extends Allay {

    /** Радиус, в котором призрак замечает игрока — сквозь что угодно. */
    public static final double DETECTION = 60.0;

    /** Раз в 1.2 секунды. */
    private static final int ATTACK_INTERVAL = 24;

    /** Куда он возвращается, оставшись без жертвы: к самому дну лабиринта. */
    private static final int HOME_DEPTH = 12;

    /**
     * Номер цели, о которой он уже объявил.
     *
     * <p>Именно номер, а не ссылка: ссылка удержала бы в памяти вышедшего из
     * игры игрока до тех пор, пока моб не сменит цель.
     */
    private int lastAnnounced = -1;

    public CometWraith(EntityType<? extends Allay> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Allay.createAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                // FLYING_SPEED здесь — единственная настройка погони: она
                // ведётся вручную, без навигации. 0.98 — двенадцать блоков
                // в секунду, вымерено.
                .add(Attributes.FLYING_SPEED, 0.98)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, DETECTION);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PhaseChaseGoal(this, ATTACK_INTERVAL));
        this.goalSelector.addGoal(5, new SinkToDepthsGoal(this, HOME_DEPTH, 0.06));
        // mustSee = false — в этом весь смысл: порода ему не помеха.
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Мозг Аллая не нужен: он про танцы и подношения, а не про охоту. */
    @Override
    protected void customServerAiStep() {
    }

    /**
     * Визг в тот миг, когда он кого-то заметил, и тишина всё остальное время.
     *
     * <p>Это единственное предупреждение, которое игрок получит: услышал —
     * значит он уже летит, и стены его не задержат.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        LivingEntity target = this.getTarget();
        int id = target == null ? -1 : target.getId();
        if (!this.level().isClientSide && target != null && id != lastAnnounced) {
            this.playSound(GimpanumSounds.WRAITH_SCREAM.get(), 4.0F, 1.0F);
        }
        lastAnnounced = id;
    }

    /** Беззвучен: ambient-звука нет вовсе. */
    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GimpanumSounds.WRAITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GimpanumSounds.WRAITH_DEATH.get();
    }

    @Override
    public void tick() {
        // Проваливаться сквозь мир призраку всё-таки не следует.
        this.noPhysics = true;
        super.tick();
        this.setNoGravity(true);
    }

    /**
     * Призрака не толкают.
     *
     * <p>Иначе крупная жертва отпихивает его ровно настолько, чтобы он завис
     * в полушаге от удара: тяга к цели и отталкивание уравновешиваются, и
     * призрак висит рядом, ничего не делая.
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }
}
