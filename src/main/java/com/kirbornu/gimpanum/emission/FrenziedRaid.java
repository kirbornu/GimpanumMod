package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import com.kirbornu.gimpanum.entity.DuneCaptain;
import com.kirbornu.gimpanum.entity.GimpanumEntities;
import com.kirbornu.gimpanum.entity.GimpanumSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Рейд Неистовых: к каждому игроку идут два-три капитана.
 *
 * <p>Капитаны появляются поодаль — не на голове, а так, чтобы толпу было видно
 * и слышно на подходе, — и сразу берут игрока целью. Отряды у них пополняются
 * в несколько раз быстрее обычного, поэтому затягивать бой невыгодно.
 *
 * <p>Место ищется там же, где игрок: стоящему на барханах — на барханах,
 * спустившемуся в лабиринт — на полу лабиринта рядом с его высотой.
 */
final class FrenziedRaid implements Emission {

    /** Попыток найти место под одного капитана. */
    private static final int TRIES = 16;

    /** Насколько выше и ниже игрока искать пол в лабиринте. */
    private static final int FLOOR_SEARCH = 8;

    @Override
    public void start(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("frenzied_raid");
        RandomSource random = level.random;
        for (ServerPlayer player : targets) {
            int captains = config.between("captains", random);
            for (int i = 0; i < captains; i++) {
                DuneCaptain captain = GimpanumEntities.DUNE_CAPTAIN.get().create(level);
                if (captain == null) {
                    continue;
                }
                Vec3 at = spot(level, player, captain, config, random);
                if (at == null) {
                    continue;
                }
                captain.moveTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
                EventHooks.finalizeMobSpawn(captain, level, level.getCurrentDifficultyAt(BlockPos.containing(at)),
                        MobSpawnType.EVENT, null);
                captain.hurry(config.number("reinforce_speedup"));
                captain.setTarget(player);
                level.addFreshEntity(captain);
            }
        }
    }

    @Override
    public boolean tick(ServerLevel level, List<ServerPlayer> targets) {
        return false;
    }

    @Nullable
    private static Vec3 spot(ServerLevel level, ServerPlayer player, DuneCaptain captain,
                             JsonConfig.Section config, RandomSource random) {
        boolean surface = GimpanumSpawner.onSurface(level, player);
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = config.between("distance_blocks", random);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int top = surface ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : player.getBlockY() + FLOOR_SEARCH;
            int bottom = surface ? top : player.getBlockY() - FLOOR_SEARCH;
            for (int y = top; y >= bottom; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                        && level.noCollision(captain, captain.getType().getDimensions()
                                .makeBoundingBox(x + 0.5, y, z + 0.5))) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }
}
