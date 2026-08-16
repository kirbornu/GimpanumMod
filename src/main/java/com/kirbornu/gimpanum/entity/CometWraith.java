package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.PhaseChaseGoal;
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

    public CometWraith(EntityType<? extends Allay> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Allay.createAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, DETECTION);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PhaseChaseGoal(this));
        // mustSee = false — в этом весь смысл: порода ему не помеха.
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Мозг Аллая не нужен: он про танцы и подношения, а не про охоту. */
    @Override
    protected void customServerAiStep() {
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
