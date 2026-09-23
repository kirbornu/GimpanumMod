package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.worldgen.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * Лихорадка Хару: больное тело горит в жару, и песок жжёт, как магма.
 *
 * <p>Жжёт только Космический песок и пепел — сама плоть диска. Спасает то же,
 * что и от магмы, и игрок это уже знает: присесть, сапоги с Ледоходом,
 * огнестойкость. Или стоять на чём угодно другом — на подложенных досках, на
 * кварце, на ледяной линзе. Урон ванильный, «от раскалённого пола»: все эти
 * защиты игра проверяет сама.
 */
final class Fever extends LastingEmission {

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("fever").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        float damage = (float) EmissionConfig.of("fever").number("damage");
        for (ServerPlayer player : targets) {
            BlockPos below = player.getOnPos();
            if (!player.onGround() || !Terrain.rock(level.getBlockState(below))) {
                continue;
            }
            if (elapsed % 10 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1, player.getZ(),
                        6, 0.4, 0.05, 0.4, 0.01);
            }
            if (player.isSteppingCarefully()) {
                continue;
            }
            // Задержка неуязвимости сама разводит удары, как у магмы: бьём
            // каждый тик, а проходит раз в полсекунды.
            if (player.hurt(level.damageSources().hotFloor(), damage) && elapsed % 20 == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.4F, 1.4F);
            }
        }
    }
}
