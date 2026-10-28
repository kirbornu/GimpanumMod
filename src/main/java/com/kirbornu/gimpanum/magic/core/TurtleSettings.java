package com.kirbornu.gimpanum.magic.core;

import java.util.List;

/**
 * Всё, от чего зависит путь черепахи.
 *
 * @param vectors        вектор каждой руны, по индексу {@link Rune}
 * @param turnAngle      угол поворота после шага, в градусах
 * @param stepMultiplier множитель длины шага
 */
public record TurtleSettings(List<Vector3> vectors, double turnAngle, double stepMultiplier) {

    public TurtleSettings {
        vectors = List.copyOf(vectors);
        if (vectors.size() != Rune.count()) {
            throw new IllegalArgumentException("векторов рун должно быть " + Rune.count() + ", а их " + vectors.size());
        }
    }

    /** Те же векторы с другими углом и шагом — так их переопределяет набор полей. */
    public TurtleSettings with(Double turnAngle, Double stepMultiplier) {
        return new TurtleSettings(vectors,
                turnAngle != null ? turnAngle : this.turnAngle,
                stepMultiplier != null ? stepMultiplier : this.stepMultiplier);
    }

    public Vector3 vector(int rune) {
        return vectors.get(rune);
    }
}
