package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.BoreChaseGoal;
import com.kirbornu.gimpanum.entity.goal.DevourBlocksGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 * <p>Догнать его нельзя: он впятеро быстрее бегущего игрока. Спрятаться за
 * стеной — тоже: породу он проедает с той же скоростью, с какой летит по
 * воздуху, и не по блоку, а полостью в свой рост (см.
 * {@link DevourBlocksGoal}).
 *
 * <p>Защиты от него нет никакой — ни света, ни стен, ни расстояния в пределах
 * чутья. Так решено намеренно: это не противник, которого переигрывают, а
 * событие, которое переживают. Остаётся одно — убить его, пока он не добрался.
 * Прежде свет обращал его в бегство, но защита, которая ставится одним факелом,
 * обесценивала весь замысел.
 */
public class SpaceDevourer extends Monster {

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
        this.xpReward = MobStats.of("space_devourer").integer("experience");
        // hoversInPlace = true: управление полётом само отключает тяготение и
        // больше его не возвращает. Иначе поглотитель падал бы всякий раз,
        // когда цель достигнута и движение остановлено, — то есть посреди
        // прогрызаемого туннеля.
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /**
     * Летает, а не ходит.
     *
     * <p>Причина не в замысле, а в непроходимости: у моба четыре блока в
     * ширину, и наземный поиск пути почти нигде не находит прохода — отсюда
     * прежнее «стоит рядом и ничего не делает». Летающему проходы не нужны
     * вовсе: он идёт к жертве по прямой, а камень на дороге проедает
     * ({@link DevourBlocksGoal}). Так он и задуман — червь, а не бегун.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // В воздухе управление полётом читает не MOVEMENT_SPEED, а
        // FLYING_SPEED, и без него моб завис бы на месте. Чутьё — сквозь
        // стены: прятаться от Поглотителя бессмысленно по замыслу, он всё
        // равно прогрызётся.
        return MobStats.attributes(Monster.createMonsterAttributes(), "space_devourer");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new BoreChaseGoal(this, MobStats.of("space_devourer").integer("attack_interval_ticks")));
        this.goalSelector.addGoal(4, new DevourBlocksGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, (HurtByTargetGoal) new HurtByTargetGoal(this)
                .setUnseenMemoryTicks(MobStats.of("space_devourer").integer("memory_ticks")));
        // Предпоследний {@code false} — «видеть цель необязательно». Поглотитель
        // чует жертву сквозь любую толщу, и это не поблажка, а весь его смысл:
        // стена от него не спасает, она лишь откладывает встречу.
        this.targetSelector.addGoal(2,
                new NearestAttackableTargetGoal<>(this, Player.class, 0, false, false, null)
                        .setUnseenMemoryTicks(MobStats.of("space_devourer").integer("memory_ticks")));
    }

    /**
     * Тяготение на поглотителя не действует никогда.
     *
     * <p>Не флагом, а вычислением — и это не придирка. {@code Entity.load}
     * присваивает признак невесомости из тега {@code NoGravity}, которого у
     * призванного и у только что загруженного из чанка моба попросту нет, а
     * отсутствующий тег читается как «нет». Выставленный в конструкторе флаг
     * поэтому доживал ровно до первой загрузки, и поглотитель падал с неба
     * камнем, пока управление полётом не спохватится.
     */
    @Override
    public boolean isNoGravity() {
        return true;
    }

    /** Падать неоткуда, но толчком вниз его всё же можно приложить о землю. */
    @Override
    protected void checkFallDamage(double distance, boolean onGround, BlockState state, BlockPos pos) {
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
