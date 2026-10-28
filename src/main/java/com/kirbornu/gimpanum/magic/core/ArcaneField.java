package com.kirbornu.gimpanum.magic.core;

/** Сфера в пространстве рун, отданная аркану. */
public record ArcaneField(String arcane, Vector3 center, double radius) {

    public boolean contains(Vector3 point) {
        return point.distanceTo(center) <= radius;
    }
}
