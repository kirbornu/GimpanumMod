package com.kirbornu.gimpanum.magic.core;

import java.util.List;
import java.util.Map;

/** Эталонные числа из постановки: векторы рун, угол 55°, шаг 1.0, демонстрационные поля и номера. */
final class Defaults {

    static final TurtleSettings TURTLE = new TurtleSettings(List.of(
            new Vector3(0.832, -0.042, 0.863),
            new Vector3(1.011, 0.397, -0.510),
            new Vector3(-0.801, 0.114, 0.887),
            new Vector3(-1.044, -0.348, -0.478),
            new Vector3(0.403, 1.119, -0.163),
            new Vector3(-0.491, 1.022, -0.392),
            new Vector3(0.561, -1.001, -0.351),
            new Vector3(-0.275, -1.138, -0.263),
            new Vector3(0.209, 0.896, 0.771),
            new Vector3(-0.067, -0.911, 0.778),
            new Vector3(0.301, 0.520, -1.039),
            new Vector3(0.112, -0.357, -1.140)), 55.0, 1.0);

    static final FieldSet FIELDS = new FieldSet("default", List.of(
            new ArcaneField("fire", new Vector3(4.72, -1.16, -0.87), 1.05),
            new ArcaneField("water", new Vector3(1.05, 2.01, -0.40), 0.72),
            new ArcaneField("heal", new Vector3(0.17, 0.75, -0.88), 1.41),
            new ArcaneField("light", new Vector3(2.86, 2.18, 1.40), 1.35),
            new ArcaneField("light", new Vector3(-0.45, 0.60, 0.80), 1.14),
            new ArcaneField("fire", new Vector3(1.00, -4.83, -1.78), 0.84)), null, null);

    static final Map<String, Integer> ORDERS = Map.of("fire", 20, "water", 60, "heal", 70, "light", 90);

    private Defaults() {
    }
}
