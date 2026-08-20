package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.entity.goal.PacedMeleeAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ходок бархан — медленный, но неотвратимый.
 *
 * <p>Зомби во всём, кроме трёх вещей. Не горит на свету: в Гимпануме вечный
 * полдень, и обычный зомби сгорел бы через десять секунд после появления.
 * Помнит замеченного десять минут — при его скорости этого хватает, чтобы
 * покрыть расстояние, на котором он вообще способен кого-то заметить.
 * И его удар оставляет след: Замедление и Слепота на десять секунд.
 *
 * <p>Убежать от него может кто угодно; вопрос в том, сколько их успеет
 * собраться по дороге.
 */
public class DuneWalker extends Zombie {

    public static final double DETECTION = 96.0;

    /**
     * Десять минут памяти о потерянной из виду цели.
     *
     * <p>Число не случайное: при скорости в блок в секунду за это время ходок
     * покрывает больше, чем радиус, в котором он способен кого-то заметить.
     * То есть замеченного он догоняет всегда — вопрос лишь во времени.
     */
    private static final int MEMORY = 12000;

    /** Раз в две с половиной секунды. */
    private static final int ATTACK_INTERVAL = 50;

    private static final int AFTERMATH = 200;

    /** Игровое время прошлого тика — по разрыву видно, что моб выпадал из прогрузки. */
    private long lastTicked = Long.MIN_VALUE;

    public DuneWalker(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 2.0)
                // 0.17 — блок в секунду, вымерено (вчетверо медленнее зомби)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, DETECTION)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        // Ванильный зомбиный удар идёт раз в секунду — заменяем своим темпом.
        this.goalSelector.removeAllGoals(goal -> goal instanceof ZombieAttackGoal);
        this.goalSelector.addGoal(2, new PacedMeleeAttackGoal(this, 1.0, ATTACK_INTERVAL));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)
                .setUnseenMemoryTicks(MEMORY));
    }

    /**
     * Выпал из прогрузки — забыл, за кем шёл.
     *
     * <p>Иначе ходок, простоявший в незагруженном чанке полдня, при первом же
     * тике продолжил бы погоню за игроком, который давно ушёл. Разрыв в
     * игровом времени и есть признак того, что моб не тикал.
     */
    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            long now = this.level().getGameTime();
            if (lastTicked != Long.MIN_VALUE && now - lastTicked > 5L) {
                this.setTarget(null);
                this.setLastHurtByMob(null);
            }
            lastTicked = now;
        }
        super.tick();
    }

    /** Удар оставляет след: уйти становится ещё труднее, чем было. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AFTERMATH, 1), this);
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, AFTERMATH), this);
        }
        return hit;
    }

    /** В Гимпануме вечный полдень — иначе ходоки сгорели бы, не сделав шага. */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        // Детёныши бегают быстро — это ровно то, чем ходок быть не должен.
        this.setBaby(false);
        return result;
    }

    /**
     * Голос подаёт только в погоне.
     *
     * <p>Ходоков много, и если бы каждый стонал просто так, пустыня звучала бы
     * как сплошной гул. А так стон означает ровно одно: тебя заметили.
     */
    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.getTarget() == null ? null : GimpanumSounds.WALKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GimpanumSounds.WALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GimpanumSounds.WALKER_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return GimpanumSounds.WALKER_STEP.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
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
