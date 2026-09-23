package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Судороги: умирающее тело бога бьётся в конвульсиях.
 *
 * <p>Раз в полминуты-четверть минуты по измерению идёт толчок: сначала гул —
 * секунда на то, чтобы понять, — потом всех, кто стоит на земле, подбрасывает.
 * При пониженной тяжести это долгий полёт, а в лабиринте — удар о свод и
 * падение на камень. Готовиться: медленное падение, Невесомость, вода.
 */
final class Convulsions extends LastingEmission {

    /** Гул опережает толчок на столько тиков. */
    private static final int WARNING = 20;

    private int nextJolt = WARNING;

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("convulsions").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("convulsions");
        if (elapsed == nextJolt - WARNING) {
            for (ServerPlayer player : targets) {
                player.playNotifySound(SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
        }
        if (elapsed < nextJolt) {
            return;
        }
        nextJolt = elapsed + config.between("interval_seconds", level.random) * 20;
        double low = config.number("launch_speed_min");
        double high = config.number("launch_speed_max");
        for (ServerPlayer player : targets) {
            if (!player.onGround()) {
                continue;
            }
            BlockState ground = level.getBlockState(player.getOnPos());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        player.getX(), player.getY(), player.getZ(), 30, 0.6, 0.1, 0.6, 0.2);
            }
            double speed = low + level.random.nextDouble() * (high - low);
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, speed, 0.0));
            player.hurtMarked = true;
        }
    }
}
