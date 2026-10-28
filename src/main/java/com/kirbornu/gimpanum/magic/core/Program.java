package com.kirbornu.gimpanum.magic.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Заклинание, разобранное на корни: к каждому привязаны его префиксы и
 * суффиксы, между корнями может стоять интерфикс.
 *
 * <p>Обобщена по типу аркана, чтобы ядро не знало, чем аркан станет в игре:
 * тестам хватает строк, игре нужны определения из настройки.
 */
public record Program<T>(List<Step<T>> steps) {

    /**
     * Один корень со всем, что к нему относится.
     *
     * @param prefixes      префиксы в порядке применения — слева направо
     * @param suffixes      суффиксы в порядке применения — слева направо
     * @param interfixAfter интерфикс между этим корнем и следующим
     */
    public record Step<T>(T root, List<T> prefixes, List<T> suffixes, Optional<T> interfixAfter) {

        public Step {
            prefixes = List.copyOf(prefixes);
            suffixes = List.copyOf(suffixes);
        }
    }

    public Program {
        steps = List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Сборка программы из арканов, ролей и отметок схлопывания. */
    public static <T> Program<T> link(List<T> arcanes, Role[] roles, boolean[] collapsed) {
        List<Step<T>> steps = new ArrayList<>();
        List<T> pendingPrefixes = new ArrayList<>();
        T root = null;
        List<T> prefixes = List.of();
        List<T> suffixes = new ArrayList<>();
        T interfix = null;
        for (int i = 0; i < arcanes.size(); i++) {
            if (collapsed[i]) {
                continue;
            }
            T arcane = arcanes.get(i);
            switch (roles[i]) {
                case PREFIX -> pendingPrefixes.add(arcane);
                case SUFFIX -> suffixes.add(arcane);
                case INTERFIX -> interfix = arcane;
                case ROOT -> {
                    if (root != null) {
                        steps.add(new Step<>(root, prefixes, suffixes, Optional.ofNullable(interfix)));
                    }
                    root = arcane;
                    prefixes = List.copyOf(pendingPrefixes);
                    pendingPrefixes.clear();
                    suffixes = new ArrayList<>();
                    interfix = null;
                }
            }
        }
        if (root != null) {
            // Грамматика не оставляет интерфиксов и префиксов после последнего
            // корня; если бы они всё же нашлись, это ошибка в таблице ролей, а не
            // в заклинании игрока, — и молча проглатывать её нельзя.
            if (interfix != null || !pendingPrefixes.isEmpty()) {
                throw new IllegalStateException("после последнего корня остались префиксы или интерфикс");
            }
            steps.add(new Step<>(root, prefixes, suffixes, Optional.empty()));
        }
        return new Program<>(steps);
    }
}
