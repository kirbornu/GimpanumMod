package com.kirbornu.gimpanum.magic.spell;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Точка, от которой префикс строит следующий выбор: голая точка или живая сущность.
 *
 * @param entity сущность или {@code null}, если это просто место
 */
public record Anchor(Vec3 point, LivingEntity entity) {

    public static Anchor of(LivingEntity entity) {
        return new Anchor(entity.getBoundingBox().getCenter(), entity);
    }

    /** Откуда смотреть, выбирая цель взглядом: у живого — от глаз. */
    public Vec3 eye() {
        return entity != null ? entity.getEyePosition() : point;
    }
}
