package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ностальгия: вокруг игроков бродят исполинские {@link Visions видения}
 * домашних животных — прошлое самого Хару.
 *
 * <p>У каждого игрока одновременно одно-два видения; растаявшее сменяется
 * новым, пока выброс не кончится.
 */
final class Nostalgia extends LastingEmission {

    /** Не чаще, чем раз в столько тиков, у одного игрока появляется новое видение. */
    private static final int SPAWN_GAP = 40;

    /** Сколько видений положено каждому игроку — бросается один раз. */
    private final Map<UUID, Integer> wanted = new HashMap<>();

    /** Тик, раньше которого игроку не показывают нового видения. */
    private final Map<UUID, Integer> cooldown = new HashMap<>();

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("nostalgia").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("nostalgia");
        RandomSource random = level.random;
        for (ServerPlayer player : targets) {
            UUID id = player.getUUID();
            int want = wanted.computeIfAbsent(id, key -> config.between("visions", random));
            if (Visions.of(player) < want && elapsed >= cooldown.getOrDefault(id, 0)) {
                cooldown.put(id, elapsed + SPAWN_GAP);
                Visions.summon(level, player, config.between("distance_blocks", random),
                        config.between("life_seconds", random) * 20);
            }
        }
    }

    @Override
    public void stop(ServerLevel level) {
        Visions.dispelAll(level);
    }
}
