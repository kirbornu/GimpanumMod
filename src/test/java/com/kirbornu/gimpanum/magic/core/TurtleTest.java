package com.kirbornu.gimpanum.magic.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Эталонные рунописи из раздела 12 постановки. Точность 1e-4 — та же, что у песочницы. */
class TurtleTest {

    private static final double EPS = 1e-4;

    @ParameterizedTest(name = "{0} → ({1}, {2}, {3}) → {4}")
    @CsvSource({
            "0,            0.8320, -0.0420,  0.8630, light",
            "3,           -1.0440, -0.3480, -0.4780, heal",
            "0 4,          0.4447,  0.9281,  1.4546, light",
            "4 0,          1.5521,  0.8139, -0.3220, water",
            "0 0 0,        2.4960, -0.1260,  2.5890, light",
            "0 8,          0.5994,  0.1470,  2.0254, light",
            "4 1 9,        0.1986,  0.6184, -1.1298, heal",
            "2 5 7 11,    -0.8593, -1.2583,  1.3204, light",
            "11 7 5 2,     0.4099,  0.4043, -1.4191, heal",
            "6 6 10 3 1,   2.7366, -1.3692, -0.3452, fire",
    })
    void reference(String runes, double x, double y, double z, String arcane) {
        int[] indices = Arrays.stream(runes.trim().split("\\s+")).mapToInt(Integer::parseInt).toArray();
        Vector3 point = Turtle.walk(indices, Defaults.TURTLE);
        assertEquals(x, point.x(), EPS, "x");
        assertEquals(y, point.y(), EPS, "y");
        assertEquals(z, point.z(), EPS, "z");
        assertEquals(arcane, Defaults.FIELDS.resolve(point));
    }

    @Test
    void sameRuneGoesStraight() {
        Vector3 one = Turtle.walk(new int[]{5}, Defaults.TURTLE);
        Vector3 four = Turtle.walk(new int[]{5, 5, 5, 5}, Defaults.TURTLE);
        assertEquals(one.x() * 4, four.x(), 1e-9);
        assertEquals(one.y() * 4, four.y(), 1e-9);
        assertEquals(one.z() * 4, four.z(), 1e-9);
    }

    @Test
    void emptyRunescriptStaysAtOrigin() {
        assertEquals(Vector3.ZERO, Turtle.walk(new int[0], Defaults.TURTLE));
    }

    @Test
    void stepMultiplierScalesWholePath() {
        TurtleSettings doubled = Defaults.TURTLE.with(null, 2.0);
        Vector3 base = Turtle.walk(new int[]{2, 5, 7, 11}, Defaults.TURTLE);
        Vector3 scaled = Turtle.walk(new int[]{2, 5, 7, 11}, doubled);
        assertEquals(base.x() * 2, scaled.x(), 1e-9);
        assertEquals(base.y() * 2, scaled.y(), 1e-9);
        assertEquals(base.z() * 2, scaled.z(), 1e-9);
    }
}
