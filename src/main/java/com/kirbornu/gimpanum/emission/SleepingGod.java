package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.entity.GimpanumEntities;
import com.kirbornu.gimpanum.entity.SpaceDevourer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;

/**
 * Кошмар Спящего Бога: на одного игрока выходит Поглотитель.
 *
 * <p>Единственное место, где Поглотитель появляется сам. Встаёт в стороне от
 * случайного игрока на его же высоте — почти наверняка в толще породы — и
 * прогрызается к нему. Это охота: жертва назначена сразу, и Поглотитель не
 * исчезает от дальности, пока её не потеряет.
 *
 * <p>Если на всей дистанции чанк не загружен, подходит ближе: ставить его в
 * незагруженный чанк значило бы получить Поглотителя, который стоит, пока
 * туда никто не придёт.
 */
final class SleepingGod implements Emission {

    /** Ближе этого не подходит, даже если дальние чанки не загружены. */
    private static final int CLOSEST = 32;

    @Override
    public void start(ServerLevel level, List<ServerPlayer> targets) {
        if (targets.isEmpty()) {
            return;
        }
        ServerPlayer prey = targets.get(level.random.nextInt(targets.size()));
        SpaceDevourer devourer = GimpanumEntities.SPACE_DEVOURER.get().create(level);
        if (devourer == null) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double y = Mth.clamp(prey.getY(), level.getMinBuildHeight() + 1,
                level.getMaxBuildHeight() - Math.ceil(devourer.getBbHeight()) - 1);
        for (int distance = EmissionConfig.of("sleeping_god").integer("distance_blocks");
             distance >= CLOSEST; distance -= 8) {
            double x = prey.getX() + Math.cos(angle) * distance;
            double z = prey.getZ() + Math.sin(angle) * distance;
            if (!level.hasChunkAt(BlockPos.containing(x, y, z))) {
                continue;
            }
            devourer.moveTo(x, y, z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
            EventHooks.finalizeMobSpawn(devourer, level, level.getCurrentDifficultyAt(devourer.blockPosition()),
                    MobSpawnType.EVENT, null);
            devourer.hunt(prey);
            level.addFreshEntity(devourer);
            return;
        }
    }

    @Override
    public boolean tick(ServerLevel level, List<ServerPlayer> targets) {
        return false;
    }
}
