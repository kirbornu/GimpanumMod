package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.AvoidLightGoal;
import com.kirbornu.gimpanum.entity.goal.DevourBlocksGoal;
import com.kirbornu.gimpanum.entity.goal.StalkAndStrikeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Поглотитель космоса — быстрый, лазающий и прогрызающий.
 *
 * <p>Догнать его нельзя: он быстрее бегущего игрока. Спрятаться за стеной —
 * тоже: стену он съест, и не по блоку, а полостью в свой рост (см.
 * {@link DevourBlocksGoal}). Единственная защита — свет: яркое место обращает
 * его в бегство, а {@link AvoidLightGoal} стоит выше цели преследования и
 * занимает тот же флаг движения, так что на свету он физически не может
 * гнаться.
 */
public class SpaceDevourer extends Monster {

    /**
     * Сколько тиков поглотитель помнит цель, потерянную из виду.
     *
     * <p>Три минуты — не круглое число ради красоты: обсидиановый блок он
     * грызёт около минуты, и памяти должно хватать на несколько слоёв подряд.
     * Иначе механика обещала бы больше, чем позволяет память.
     */
    private static final int MEMORY = 3600;

    /** Раз в две секунды, как и просили: удар редкий, но тяжёлый. */
    private static final int ATTACK_INTERVAL = 40;

    /** Как часто вопить, пока идёт погоня: раз в четыре секунды с разбросом. */
    private static final int CHASE_CRY = 80;

    /**
     * Номер цели, о которой он уже объявил.
     *
     * <p>Именно номер, а не ссылка: ссылка удержала бы в памяти вышедшего из
     * игры игрока до тех пор, пока моб не сменит цель.
     */
    private int lastAnnounced = -1;

    /** Сколько тиков осталось до следующего вопля в погоне. */
    private int chaseCry;

    public SpaceDevourer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                // 0.40 — семь блоков в секунду, вымерено на прямом отрезке;
                // связь атрибута со скоростью нелинейная, по формуле не угадать
                .add(Attributes.MOVEMENT_SPEED, 0.40)
                // Восемьдесят блоков — и сквозь стены: прятаться от Поглотителя
                // бессмысленно по замыслу, он всё равно прогрызётся.
                .add(Attributes.FOLLOW_RANGE, 80.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidLightGoal(this, 1.3));
        this.goalSelector.addGoal(3, new StalkAndStrikeGoal(this, 1.0, ATTACK_INTERVAL));
        this.goalSelector.addGoal(4, new DevourBlocksGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, (HurtByTargetGoal) new HurtByTargetGoal(this)
                .setUnseenMemoryTicks(MEMORY));
        // Предпоследний {@code false} — «видеть цель необязательно». Поглотитель
        // чует жертву сквозь любую толщу, и это не поблажка, а весь его смысл:
        // стена от него не спасает, она лишь откладывает встречу.
        this.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(this, Player.class, 0, false, false, null)
                        .setUnseenMemoryTicks(MEMORY));
    }

    /** Лазает по отвесному, как паук: упёрся — значит полез. */
    @Override
    public boolean onClimbable() {
        return this.horizontalCollision;
    }

    /**
     * Рёв — при выборе жертвы и потом всю погоню.
     *
     * <p>Один раз при захвате мало: Поглотитель идёт за жертвой минутами и
     * сквозь стены, и всё это время он должен быть слышен. Иначе выходит
     * тишина, из которой внезапно выламывается стена, — а нужно, чтобы
     * приближение было слышно заранее и с каждым разом ближе.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        LivingEntity target = this.getTarget();
        int id = target == null ? -1 : target.getId();
        if (target != null && (id != lastAnnounced || --chaseCry <= 0)) {
            this.playSound(GimpanumSounds.DEVOURER_ROAR.get(), 2.0F, 1.0F);
            chaseCry = CHASE_CRY + this.random.nextInt(CHASE_CRY / 2);
        }
        lastAnnounced = id;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GimpanumSounds.DEVOURER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GimpanumSounds.DEVOURER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GimpanumSounds.DEVOURER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(GimpanumSounds.DEVOURER_STEP.get(), 0.6F, 1.0F);
    }

    /**
     * Убрать трещины, если поглотитель погиб посреди укуса.
     *
     * <p>Цели не останавливаются, когда сущность убирают из мира, поэтому
     * узор разрушения остался бы на блоке до следующего обновления.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            this.level().destroyBlockProgress(this.getId(), this.blockPosition(), -1);
        }
        super.remove(reason);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
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
