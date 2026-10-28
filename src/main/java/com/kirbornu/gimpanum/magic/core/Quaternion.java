package com.kirbornu.gimpanum.magic.core;

/**
 * Кватернион {@code [w, x, y, z]} — ровно столько, сколько нужно черепахе.
 *
 * <p>Свой, а не из JOML: там всё во {@code float}, а песочница в браузере
 * считает в {@code double}, и четвёртый знак после запятой разъехался бы уже
 * на третьей руне.
 */
public record Quaternion(double w, double x, double y, double z) {

    public static final Quaternion IDENTITY = new Quaternion(1, 0, 0, 0);

    /** Произведение Гамильтона {@code this ⊗ other}. */
    public Quaternion multiply(Quaternion o) {
        return new Quaternion(
                w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }

    public Quaternion conjugate() {
        return new Quaternion(w, -x, -y, -z);
    }

    /** {@code (q ⊗ (0, v) ⊗ q*).xyz}. */
    public Vector3 rotate(Vector3 v) {
        Quaternion r = multiply(new Quaternion(0, v.x(), v.y(), v.z())).multiply(conjugate());
        return new Vector3(r.x, r.y, r.z);
    }

    /** Поворот на {@code radians} вокруг оси {@code axis}; ось нормируется здесь. */
    public static Quaternion axisAngle(Vector3 axis, double radians) {
        double length = axis.length();
        if (length == 0) {
            // Нулевой вектор руны допустим в настройке, но оси у него нет — не
            // поворачиваем вовсе, чем делить на ноль и плодить NaN.
            return IDENTITY;
        }
        double s = Math.sin(radians / 2) / length;
        return new Quaternion(Math.cos(radians / 2), axis.x() * s, axis.y() * s, axis.z() * s);
    }
}
