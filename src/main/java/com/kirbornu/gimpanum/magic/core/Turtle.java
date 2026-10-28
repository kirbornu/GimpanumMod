package com.kirbornu.gimpanum.magic.core;

/**
 * Рунопись → точка.
 *
 * <p>Черепаха стоит в нуле лицом «никуда» и для каждой руны слева направо
 * делает шаг на вектор руны в своём текущем базисе, а затем поворачивается
 * вокруг оси этой же руны. Поворот домножается справа, то есть вокруг оси в
 * текущем базисе. Поэтому одна и та же руна подряд идёт прямой линией: ось
 * поворота совпадает с направлением шага, и поворот шаг не меняет.
 */
public final class Turtle {

    private Turtle() {
    }

    /** @param runes индексы рун рунописи */
    public static Vector3 walk(int[] runes, TurtleSettings settings) {
        double turn = Math.toRadians(settings.turnAngle());
        Vector3 position = Vector3.ZERO;
        Quaternion heading = Quaternion.IDENTITY;
        for (int rune : runes) {
            Vector3 vector = settings.vector(rune);
            position = position.add(heading.rotate(vector.scale(settings.stepMultiplier())));
            heading = heading.multiply(Quaternion.axisAngle(vector, turn));
        }
        return position;
    }
}
