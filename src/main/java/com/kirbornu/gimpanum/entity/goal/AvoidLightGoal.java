package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Бегство из освещённого места в темноту.
 *
 * <p>Стоит выше цели преследования и занимает тот же флаг движения, поэтому
 * пока моб на свету, гнаться он не может физически — сначала уйдёт в тень.
 * Это и есть «обходит освещённую зону»: путь ищется до тёмной клетки, а не
 * просто прочь от света, так что поглотитель огибает освещённый коридор, если
 * обход есть.
 */
public class AvoidLightGoal extends Goal {

    /** Ярче этого — бежать. Факел вблизи даёт 14, дневное небо 15. */
    private static final int BRIGHT = 12;
    private static final int SEARCH_RADIUS = 16;
    private static final int TRIES = 24;

    private final PathfinderMob mob;
    private final double speed;

    @Nullable
    private Path path;

    public AvoidLightGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (brightness(mob.blockPosition()) < BRIGHT) {
            return false;
        }
        BlockPos shelter = findDark();
        if (shelter == null) {
            return false;
        }
        path = mob.getNavigation().createPath(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 0);
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone() && brightness(mob.blockPosition()) >= BRIGHT;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, speed);
    }

    @Override
    public void stop() {
        path = null;
    }

    @Nullable
    private BlockPos findDark() {
        BlockPos best = null;
        int bestLight = BRIGHT;
        for (int i = 0; i < TRIES; i++) {
            BlockPos candidate = mob.blockPosition().offset(
                    mob.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS,
                    mob.getRandom().nextInt(9) - 4,
                    mob.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS);
            int light = brightness(candidate);
            if (light < bestLight && mob.level().getBlockState(candidate).isAir()) {
                bestLight = light;
                best = candidate;
            }
        }
        return best;
    }

    private int brightness(BlockPos pos) {
        return mob.level().getMaxLocalRawBrightness(pos);
    }
}
