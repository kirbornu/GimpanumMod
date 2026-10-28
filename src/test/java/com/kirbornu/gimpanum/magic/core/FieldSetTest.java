package com.kirbornu.gimpanum.magic.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldSetTest {

    private static FieldSet set(ArcaneField... fields) {
        return new FieldSet("test", List.of(fields), null, null);
    }

    @Test
    void smallerRadiusWinsInside() {
        FieldSet fields = set(
                new ArcaneField("big", new Vector3(0, 0, 0), 5),
                new ArcaneField("small", new Vector3(1, 0, 0), 1));
        assertEquals("small", fields.resolve(new Vector3(1.5, 0, 0)));
    }

    @Test
    void boundaryCountsAsInside() {
        FieldSet fields = set(
                new ArcaneField("big", new Vector3(0, 0, 0), 5),
                new ArcaneField("small", new Vector3(1, 0, 0), 1));
        assertEquals("small", fields.resolve(new Vector3(2, 0, 0)));
    }

    @Test
    void equalRadiusNearestCentreWins() {
        FieldSet fields = set(
                new ArcaneField("a", new Vector3(0, 0, 0), 2),
                new ArcaneField("b", new Vector3(1, 0, 0), 2));
        assertEquals("b", fields.resolve(new Vector3(0.9, 0, 0)));
        assertEquals("a", fields.resolve(new Vector3(0.1, 0, 0)));
    }

    @Test
    void fullTieGoesToEarlierField() {
        FieldSet fields = set(
                new ArcaneField("a", new Vector3(-1, 0, 0), 2),
                new ArcaneField("b", new Vector3(1, 0, 0), 2));
        assertEquals("a", fields.resolve(new Vector3(0, 0, 0)));
    }

    @Test
    void outsideNearestCentreIgnoresRadius() {
        FieldSet fields = set(
                new ArcaneField("huge", new Vector3(0, 0, 0), 9),
                new ArcaneField("tiny", new Vector3(20, 0, 0), 0.1));
        // До края огромного поля ближе, но решает центр.
        assertEquals("tiny", fields.resolve(new Vector3(14, 0, 0)));
    }

    @Test
    void outsideTieGoesToEarlierField() {
        FieldSet fields = set(
                new ArcaneField("a", new Vector3(-5, 0, 0), 1),
                new ArcaneField("b", new Vector3(5, 0, 0), 1));
        assertEquals("a", fields.resolve(new Vector3(0, 0, 0)));
    }

    @Test
    void emptySetHasNoArcane() {
        assertThrows(IllegalStateException.class, () -> set().resolve(Vector3.ZERO));
    }
}
