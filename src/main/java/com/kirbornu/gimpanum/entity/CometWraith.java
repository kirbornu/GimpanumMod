package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.AllAroundTargetGoal;
import com.kirbornu.gimpanum.entity.goal.PhaseChaseGoal;
import com.kirbornu.gimpanum.entity.goal.SinkToDepthsGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Enemy;
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
 * породу на всю дальность чутья и сквозь неё же летит. Прятаться от
 * него бесполезно, можно только уйти — наверх.
 *
 * <p>Потому что лабиринт — его предел. Выше {@code max_y} он не поднимается и
 * тех, кто выше, не замечает; погоню бросает, как только жертва туда ушла.
 * Число берётся из настройки и по умолчанию равно 64 — там по генерации
 * кончаются пещеры, а барханы начинаются не ниже 72-го блока. Мерить именно
 * высотой, а не открытым небом: игрок, прокопавший шахту в лабиринт, должен
 * оставаться добычей, хотя над ним и видно небо.
 *
 * <p>{@link Enemy} — метка враждебного моба. Аллай ею не помечен, и без неё
 * призрака можно было бы водить на поводке, а големы не видели бы в нём врага.
 */
public class CometWraith extends Allay implements Enemy {

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
        this.xpReward = MobStats.of("comet_wraith").integer("experience");
    }

    public static AttributeSupplier.Builder createAttributes() {
        // FLYING_SPEED здесь — единственная настройка погони: она ведётся
        // вручную, без навигации.
        return MobStats.attributes(Allay.createAttributes(), "comet_wraith");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PhaseChaseGoal(this, MobStats.of("comet_wraith").integer("attack_interval_ticks")));
        this.goalSelector.addGoal(5, new SinkToDepthsGoal(this, HOME_DEPTH, 0.06));
        // mustSee = false — в этом весь смысл: порода ему не помеха.
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new AllAroundTargetGoal(this, MobStats.of("comet_wraith").integer("memory_ticks"),
                target -> target.getY() < ceiling()));
    }

    /** Выше этого он не поднимается и никого не замечает. */
    private static int ceiling() {
        return MobStats.of("comet_wraith").integer("max_y");
    }

    /**
     * Мозг Аллая не нужен: он про танцы и подношения, а не про охоту.
     *
     * <p>Здесь только отпускаем жертву, ушедшую выше потолка. Выбор цели её
     * такую не возьмёт, но уже взятую ванильная память держит до последнего,
     * и отвечать на удар она тоже заставила бы.
     */
    @Override
    protected void customServerAiStep() {
        LivingEntity target = this.getTarget();
        if (target != null && target.getY() >= ceiling()) {
            this.setTarget(null);
        }
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
        // Потолок — жёстко, а не тягой вниз: на полном ходу он проходит три
        // блока за тик и мягкую преграду проскочил бы насквозь.
        if (!this.level().isClientSide && this.getY() > ceiling()) {
            this.setPos(this.getX(), ceiling(), this.getZ());
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
    }

    /**
     * Предметов призрак не берёт.
     *
     * <p>У Аллая правая кнопка с предметом в руке отдаёт ему этот предмет и
     * делает дарителя «любимым игроком», а любимый игрок Аллаю урона не
     * наносит вовсе. Для призрака это значило бы: ткнул в него факелом — и
     * больше не можешь его ранить, хотя он продолжает на тебя охотиться.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
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
