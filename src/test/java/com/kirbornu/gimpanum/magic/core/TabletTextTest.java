package com.kirbornu.gimpanum.magic.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletTextTest {

    private static final int E = TabletText.EMPTY;

    @Test
    void referenceGrid() {
        // [АН, _, _] [_, ДАР, БЭ] [_, _, _]
        List<int[]> words = TabletText.read(new int[]{0, E, E, E, 4, 1, E, E, E});
        assertEquals(2, words.size());
        assertArrayEquals(new int[]{0}, words.get(0));
        assertArrayEquals(new int[]{4, 1}, words.get(1));
    }

    @Test
    void emptyTablet() {
        assertTrue(TabletText.read(new int[]{E, E, E, E}).isEmpty());
    }

    @Test
    void wordContinuesAcrossLineEnd() {
        // 2×2: [АН, БЭ] [ВОЛ, _] — конец строки не пробел.
        List<int[]> words = TabletText.read(new int[]{0, 1, 2, E});
        assertEquals(1, words.size());
        assertArrayEquals(new int[]{0, 1, 2}, words.get(0));
    }
}
