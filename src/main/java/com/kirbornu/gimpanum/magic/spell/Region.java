package com.kirbornu.gimpanum.magic.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Область формы: что внутри и какие блоки в неё попали.
 *
 * <p>Блок внутри, если внутри его центр. Так у формы нет «половинчатых»
 * блоков, и одна и та же форма в одном и том же месте всегда выделяет одно и
 * то же.
 */
public record Region(AABB bounds, Predicate<Vec3> inside) {

    public boolean contains(Vec3 point) {
        return inside.test(point);
    }

    public List<BlockPos> blocks() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (inside.test(Vec3.atCenterOf(pos))) {
                out.add(pos.immutable());
            }
        }
        return out;
    }

    public static Region sphere(Vec3 center, double radius) {
        return new Region(new AABB(center, center).inflate(radius), p -> p.distanceTo(center) <= radius);
    }

    /**
     * Конус от вершины по направлению.
     *
     * @param halfAngle половина угла раствора, в радианах
     */
    public static Region cone(Vec3 apex, Vec3 direction, double halfAngle, double length) {
        Vec3 axis = direction.normalize();
        double cos = Math.cos(halfAngle);
        return new Region(new AABB(apex, apex).inflate(length), p -> {
            Vec3 d = p.subtract(apex);
            double along = d.dot(axis);
            double distance = d.length();
            // Вершина конуса — его часть: иначе узкий конус у самых ног пустел бы.
            return distance <= length && (distance < 1.0E-6 || along >= distance * cos);
        });
    }
}
