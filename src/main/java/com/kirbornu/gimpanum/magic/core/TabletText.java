package com.kirbornu.gimpanum.magic.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Чтение таблички: построчно слева направо, сверху вниз.
 *
 * <p>Пустая ячейка — пробел, несколько пустых подряд — один пробел, пустые в
 * начале и в конце не значат ничего. Конец строки пробелом не считается:
 * рунопись может переходить со строки на строку, иначе длинные слова не
 * поместились бы на узкую табличку.
 */
public final class TabletText {

    /** Значение пустой ячейки. */
    public static final int EMPTY = -1;

    private TabletText() {
    }

    /**
     * @param cells индексы рун построчно, {@link #EMPTY} — пусто; длина — {@code rows * columns}
     * @return рунописи по порядку; пустой список — табличка пуста
     */
    public static List<int[]> read(int[] cells) {
        List<int[]> words = new ArrayList<>();
        List<Integer> word = new ArrayList<>();
        for (int cell : cells) {
            if (cell == EMPTY) {
                flush(word, words);
            } else {
                word.add(cell);
            }
        }
        flush(word, words);
        return words;
    }

    private static void flush(List<Integer> word, List<int[]> words) {
        if (!word.isEmpty()) {
            words.add(word.stream().mapToInt(Integer::intValue).toArray());
            word.clear();
        }
    }
}
