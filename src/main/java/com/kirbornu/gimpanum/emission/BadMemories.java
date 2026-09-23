package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import com.kirbornu.gimpanum.entity.GimpanumEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Плохие Воспоминания: рядом с игроками то и дело вспыхивает прошлое.
 *
 * <p>У каждого игрока свой отсчёт до следующего воспоминания, чтобы они не
 * рвались у всех разом. Появляется воспоминание в паре шагов — искры и шипение
 * говорят «беги», и пары секунд как раз хватает, чтобы выбежать из радиуса.
 */
final class BadMemories extends LastingEmission {

    /** Попыток найти свободное место рядом с игроком. */
    private static final int TRIES = 8;

    /** Тик, на котором у игрока появится следующее воспоминание. */
    private final Map<UUID, Integer> next = new HashMap<>();

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("bad_memories").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("bad_memories");
        RandomSource random = level.random;
        for (ServerPlayer player : targets) {
            int due = next.computeIfAbsent(player.getUUID(),
                    id -> elapsed + config.between("interval_seconds", random) * 20);
            if (elapsed < due) {
                continue;
            }
            next.put(player.getUUID(), elapsed + config.between("interval_seconds", random) * 20);
            summon(level, player, config, random);
        }
    }

    /**
     * Точка в паре шагов от игрока — в воздухе, а не в стене.
     *
     * <p>Воспоминание внутри породы взорвалось бы впустую: стены прикрывают от
     * него так же, как от обычного взрыва, а из камня прикрыто всё. Не нашлось
     * свободного места — прямо над головой игрока.
     */
    private static Vec3 spot(ServerLevel level, ServerPlayer player, JsonConfig.Section config, RandomSource random) {
        double near = config.number("distance_blocks_min");
        double far = config.number("distance_blocks_max");
        for (int attempt = 0; attempt < TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = near + random.nextDouble() * (far - near);
            Vec3 at = new Vec3(player.getX() + Math.cos(angle) * distance,
                    player.getY() + 0.5 + random.nextDouble() * 1.5,
                    player.getZ() + Math.sin(angle) * distance);
            BlockPos pos = BlockPos.containing(at);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return at;
            }
        }
        return player.position().add(0.0, player.getBbHeight() + 0.5, 0.0);
    }

    private static void summon(ServerLevel level, ServerPlayer player, JsonConfig.Section config, RandomSource random) {
        MemoryEntity memory = GimpanumEntities.MEMORY.get().create(level);
        if (memory == null) {
            return;
        }
        memory.moveTo(spot(level, player, config, random));
        memory.arm(config.integer("fuse_ticks"), (float) config.number("radius_blocks"),
                (float) config.number("damage"));
        level.addFreshEntity(memory);
    }
}
