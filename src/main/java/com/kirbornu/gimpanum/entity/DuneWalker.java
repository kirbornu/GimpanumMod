package com.kirbornu.gimpanum.entity;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Ходок бархан — медленный, но неотвратимый.
 *
 * <p>Зомби во всём, кроме двух вещей. Первая: он не горит на свету — в
 * Гимпануме вечный полдень, и обычный зомби сгорел бы через десять секунд
 * после появления. Вторая: он замечает игрока за {@value #DETECTION} блоков и
 * с этого мгновения идёт к нему, куда бы тот ни ушёл. Обогнать его может
 * кто угодно; вопрос в том, сколько их успеет собраться по дороге.
 */
public class DuneWalker extends Zombie {

    public static final double DETECTION = 96.0;

    /**
     * Десять минут памяти о потерянной из виду цели.
     *
     * <p>Число не случайное: при скорости в четверть блока в секунду за это
     * время ходок покрывает больше, чем радиус, в котором он вообще способен
     * кого-то заметить. То есть замеченного он догоняет всегда — вопрос
     * только в том, сколько у игрока есть времени.
     */
    private static final int MEMORY = 12000;

    public DuneWalker(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0)
                // Втрое медленнее зомби: 0.23 против 0.08.
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.FOLLOW_RANGE, DETECTION)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    /**
     * Заметив игрока, ходок не забывает его пять минут.
     *
     * <p>Это и делает его неотвратимым: убежать можно, оторваться — нет,
     * он придёт следом, сколько бы времени на это ни ушло.
     */
    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true)
                .setUnseenMemoryTicks(MEMORY));
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
}
