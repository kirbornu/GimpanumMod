package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Круговое чутьё: подкрасться к некрофагу нельзя.
 *
 * <p>Ванильный выбор жертвы проверяет две вещи, и обе здесь мешают. Первая —
 * прямая видимость: моб не берёт в цель того, кого не видит, и стена от него
 * спасает. Вторая тоньше и обиднее: дальность обнаружения умножается на
 * {@code getVisibilityPercent} жертвы, а он падает от приседания и от брони
 * — отсюда и «подкрался сзади», хотя никаких углов обзора в игре нет вовсе.
 *
 * <p>Некрофаги устроены иначе: это не звери, а падальщики мёртвого измерения,
 * и чуют они присутствие, а не силуэт. Обе проверки сняты, остаётся чистое
 * расстояние — {@code FOLLOW_RANGE} у каждого свой и задаёт, насколько далеко
 * простирается чутьё.
 *
 * <p>Память о потерянной цели тоже задаётся здесь: без неё жертва забывается
 * через шестьдесят тиков, и моб, идущий сквозь стену, бросал бы погоню
 * посреди камня.
 */
public class AllAroundTargetGoal extends NearestAttackableTargetGoal<Player> {

    /**
     * @param memoryTicks сколько тиков помнить цель, потерянную из виду
     */
    public AllAroundTargetGoal(Mob mob, int memoryTicks) {
        this(mob, memoryTicks, null);
    }

    /**
     * @param memoryTicks сколько тиков помнить цель, потерянную из виду
     * @param admits      кого вообще можно заметить, сверх расстояния
     */
    public AllAroundTargetGoal(Mob mob, int memoryTicks, @Nullable Predicate<LivingEntity> admits) {
        // randomInterval 0 — искать каждый тик: чутьё не должно мигать.
        // mustSee и mustReach — false: ни видеть, ни дойти не обязательно.
        super(mob, Player.class, 0, false, false, admits);
        this.setUnseenMemoryTicks(memoryTicks);
        this.targetConditions = this.targetConditions
                .ignoreLineOfSight()
                .ignoreInvisibilityTesting();
    }

    /**
     * Дальность чутья берётся заново перед каждым поиском.
     *
     * <p>Предок запоминает её один раз, в конструкторе, — а цели моба
     * строятся раньше, чем {@link com.kirbornu.gimpanum.entity.MobStats}
     * назначит ему числа из файла. Без этого правка {@code detection_blocks}
     * меняла бы только то, как долго моб держит цель, но не то, с какого
     * расстояния он её замечает.
     */
    @Override
    public boolean canUse() {
        this.targetConditions.range(this.getFollowDistance());
        return super.canUse();
    }
}
