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
public record Region(AABB bounds, Predicate<Vec3> inside, Predicate<Vec3> entityInside) {

    /** Область, где для блоков и для существ одно правило. */
    public Region(AABB bounds, Predicate<Vec3> inside) {
        this(bounds, inside, inside);
    }

    /** Попадает ли существо, если {@code point} — центр его рамки. */
    public boolean contains(Vec3 point) {
        return entityInside.test(point);
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

    /** Прямоугольник {@code x × y × z} блоков с центром в точке. */
    public static Region box(Vec3 center, double x, double y, double z) {
        return new Region(new AABB(center, center).inflate(x / 2, y / 2, z / 2),
                p -> Math.abs(p.x - center.x) <= x / 2 && Math.abs(p.y - center.y) <= y / 2
                        && Math.abs(p.z - center.z) <= z / 2);
    }

    /**
     * Луч длиной {@code length} и шириной {@code width} от точки по направлению:
     * всё, что не дальше {@code width / 2} от оси.
     */
    public static Region line(Vec3 start, Vec3 direction, double length, double width) {
        Vec3 axis = direction.normalize();
        double half = width / 2;
        Vec3 end = start.add(axis.scale(length));
        return new Region(new AABB(start, end).inflate(half), p -> {
            Vec3 d = p.subtract(start);
            double along = d.dot(axis);
            return along >= 0 && along <= length && d.subtract(axis.scale(along)).length() <= half;
        });
    }

    /**
     * Кольцо на высоте {@code floorY}: блоки — один слой, чьё горизонтальное
     * расстояние до центра от {@code radius − thickness/2} до
     * {@code radius + thickness/2}. Существа — те, кто стоит в кольце, то
     * есть их центр не ниже слоя и не выше роста человека над ним.
     */
    public static Region ring(Vec3 center, int floorY, double radius, double thickness) {
        double inner = Math.max(0, radius - thickness / 2);
        double outer = radius + thickness / 2;
        Predicate<Vec3> flat = p -> {
            double dx = p.x - center.x;
            double dz = p.z - center.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            return d >= inner && d <= outer;
        };
        return new Region(new AABB(center.x - outer, floorY, center.z - outer, center.x + outer, floorY + 3,
                center.z + outer),
                p -> flat.test(p) && p.y >= floorY && p.y < floorY + 1,
                p -> flat.test(p) && p.y >= floorY - 0.5 && p.y <= floorY + 2.5);
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
