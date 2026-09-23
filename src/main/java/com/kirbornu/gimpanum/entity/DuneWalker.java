package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.config.JsonConfig;
import com.kirbornu.gimpanum.entity.goal.AllAroundTargetGoal;
import com.kirbornu.gimpanum.entity.goal.FollowCaptainGoal;
import com.kirbornu.gimpanum.entity.goal.PacedMeleeAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ходок бархан — солдат в отряде {@link DuneCaptain капитана}.
 *
 * <p>Зомби во всём, кроме нескольких вещей. Не горит на свету: в Гимпануме
 * вечный полдень, и обычный зомби сгорел бы через десять секунд после
 * появления. Его удар оставляет след: Замедление и Слепота на десять секунд.
 *
 * <p>Сам по себе солдат почти слеп — чует лишь в нескольких шагах. Сам он и не
 * появляется: его приводит капитан, и дальше солдат держится в его толпе,
 * пока капитан не спустит отряд с поводка. Тогда солдат бросается на ту же
 * жертву; когда капитан её теряет — возвращается в строй. Заметивший кого-то
 * вблизи бросается и без приказа, а удар по любому бойцу спускает весь отряд.
 *
 * <p>Капитан погиб — солдат осиротел. Сирота стоит на месте, дерётся с тем,
 * кто подошёл вплотную, и понемногу истлевает; от такой смерти с него ничего
 * не падает. Сиротами же становятся и ходоки из старых миров, и солдат из
 * яйца: капитана у них нет и не было.
 */
public class DuneWalker extends Zombie {

    private static final int AFTERMATH = 200;

    /**
     * Сколько тиков солдат ждёт, не найдя капитана в мире, прежде чем уйти.
     *
     * <p>Капитан, исчезнувший от дальности, забирает с собой всех, кто
     * загружен рядом. Но солдат мог стоять в соседнем, невыгруженном чанке —
     * он проснётся потом, а капитана нет и уже не будет. Пять секунд — с
     * запасом на то, чтобы капитан успел подгрузиться, если он просто рядом.
     */
    private static final int MISSING_LIMIT = 100;

    /** Капитан отряда; {@code null} — сирота. */
    @Nullable
    private UUID captain;

    /** Сколько тиков подряд капитана нет в мире, хотя он и не погиб. */
    private int missing;

    /** Спущен с поводка и дерётся за цель капитана. */
    private boolean unleashed;

    /** Игровое время прошлого тика — по разрыву видно, что моб выпадал из прогрузки. */
    private long lastTicked = Long.MIN_VALUE;

    public DuneWalker(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = MobStats.of(this.stats()).integer("experience");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MobStats.attributes(Zombie.createAttributes(), "dune_walker")
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    /**
     * Раздел настройки с числами этого вида.
     *
     * <p>Метод, а не поле: он нужен уже при расстановке целей, а их игра
     * расставляет из конструктора предка — раньше, чем заполнятся поля.
     */
    protected String stats() {
        return "dune_walker";
    }

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        // Ванильный зомбиный удар идёт раз в секунду — заменяем своим темпом.
        this.goalSelector.removeAllGoals(goal -> goal instanceof ZombieAttackGoal);
        this.goalSelector.addGoal(2, new PacedMeleeAttackGoal(this, 1.0, MobStats.of(this.stats()).integer("attack_interval_ticks")));
        this.targetSelector.addGoal(1, new AllAroundTargetGoal(this, MobStats.of(this.stats()).integer("memory_ticks")));
        this.addSquadGoals();
    }

    /**
     * Солдат не бродит сам по себе: он либо в строю, либо стоит на месте.
     */
    protected void addSquadGoals() {
        this.goalSelector.removeAllGoals(goal -> goal instanceof WaterAvoidingRandomStrollGoal
                || goal instanceof MoveThroughVillageGoal);
        this.goalSelector.addGoal(3, new FollowCaptainGoal(this));
    }

    /** Капитан этого солдата, если он жив и сейчас в мире. */
    @Nullable
    public DuneCaptain leader() {
        if (captain == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(captain) instanceof DuneCaptain found && found.isAlive() ? found : null;
    }

    /** Кто отвечает за этот отряд: у солдата — его капитан, у капитана — он сам. */
    @Nullable
    protected DuneCaptain commander() {
        return this.leader();
    }

    boolean serves(UUID id) {
        return id.equals(captain);
    }

    void enlist(UUID id) {
        captain = id;
    }

    /** Капитан погиб: дальше сам по себе, и цель — только та, что рядом. */
    void orphan() {
        captain = null;
        unleashed = false;
        this.setTarget(null);
    }

    /** Истлел ли он без капитана — от этого с него ничего не падает. */
    public boolean withered() {
        DamageSource last = this.getLastDamageSource();
        return last != null && last.is(DamageTypes.STARVE);
    }

    /**
     * Поводок, сиротство и ожидание капитана.
     *
     * <p>Спущенный солдат получает цель капитана, только если своей у него
     * нет: того, кто уже дерётся с кем-то вблизи, не отзываем.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (captain == null) {
            wither();
            return;
        }
        DuneCaptain leader = this.leader();
        if (leader == null) {
            if (++missing > MISSING_LIMIT) {
                this.discard();
            }
            return;
        }
        missing = 0;
        LivingEntity prey = leader.getTarget();
        if (leader.unleashed() && prey != null) {
            unleashed = true;
            if (this.getTarget() == null) {
                this.setTarget(prey);
            }
        } else if (unleashed) {
            unleashed = false;
            this.setTarget(null);
        }
    }

    /**
     * Сирота истлевает — понемногу, но до конца.
     *
     * <p>Урон «от голода»: мобы им больше никогда не получают, поэтому по нему
     * смерть от сиротства видна безошибочно — см. {@link #withered()}.
     */
    private void wither() {
        JsonConfig.Section stats = MobStats.of("dune_walker");
        int every = Math.max(1, stats.integer("orphan_damage_interval_ticks"));
        if (this.tickCount % every == 0) {
            this.hurt(this.damageSources().starve(), (float) stats.number("orphan_damage"));
        }
    }

    /** Удар по любому бойцу спускает весь отряд. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof Player player
                && !player.isCreative() && !player.isSpectator()) {
            DuneCaptain leader = this.commander();
            if (leader != null) {
                leader.alarm(player);
            }
        }
        return hurt;
    }

    /** Солдата уводит капитан, сам по себе он не исчезает — иначе строй таял бы по одному. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return captain == null && super.removeWhenFarAway(distance);
    }

    @Override
    public boolean shouldDropExperience() {
        return super.shouldDropExperience() && !this.withered();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (captain != null) {
            tag.putUUID("Captain", captain);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        captain = tag.hasUUID("Captain") ? tag.getUUID("Captain") : null;
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
        // Свои данные группы вместо пустых: иначе зомби сам бросает жребий на
        // детёныша, а детёныш — на курицу, и ходок уезжал верхом. Детёныши
        // к тому же бегают быстро — ровно то, чем ходок быть не должен.
        return super.finalizeSpawn(level, difficulty, spawnType, new ZombieGroupData(false, false));
    }

    /**
     * Без ванильных надбавок при появлении.
     *
     * <p>Зомби при появлении получает случайную прибавку к чутью — до двух с
     * половиной раз, изредка становится вожаком с учетверённым здоровьем, а
     * шанс звать подкрепление выставляет себе сам, поверх нуля из атрибутов.
     * Всё это молча искажало числа из настройки, а подкрепление на высокой
     * сложности и вовсе звало обычных зомби, которые в вечный полдень горят.
     */
    @Override
    protected void handleAttributes(float difficulty) {
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
