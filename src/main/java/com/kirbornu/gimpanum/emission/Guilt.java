package com.kirbornu.gimpanum.emission;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Приступ Вины: удар по некрофагу возвращается ударившему.
 *
 * <p>Вина Этерисов, которые создали Веритов и видели, во что их превратили.
 * Некрофаги — это бывшие Вериты, и каждый удар по ним отзывается в ударившем
 * долей нанесённого урона, мимо брони. Драться становится дороже, чем бежать.
 *
 * <p>Сам отзыв — в {@link Emissions}: он случается в миг удара, а не по тику.
 */
final class Guilt extends LastingEmission {

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("guilt").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
    }

    /** Какая доля нанесённого урона возвращается. */
    static double share() {
        return EmissionConfig.of("guilt").number("reflected_share");
    }
}
