package com.kirbornu.gimpanum.magic.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarTest {

    /** Роли строкой: R/P/S/I, схлопнутый — с «*». */
    private static String describe(int[] orders) {
        Role[] roles = Grammar.roles(orders);
        boolean[] collapsed = Grammar.collapsed(roles);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            out.add(roles[i].name().charAt(0) + (collapsed[i] ? "*" : ""));
        }
        return String.join(" ", out);
    }

    private static int[] parse(String orders) {
        return Arrays.stream(orders.trim().split("\\s+")).mapToInt(Integer::parseInt).toArray();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "70,                 R",
            "70 20,              R S",
            "20 70,              P R",
            "20 70 60 90,        P R I R",
            "90 20 60 70,        R I P R",
            "50 30 30 60,        R S P R",
            "50 30 30 30 60,     R S I P R",
            "50 50 50,           R I R",
            "50 50,              R R*",
            "30 30 50,           R P R",
    })
    void reference(String orders, String roles) {
        assertEquals(roles, describe(parse(orders)));
    }

    @Test
    void consecutiveInterfixesCollapse() {
        // 50 · 30 30 30 30 · 60: середина — два интерфикса подряд.
        assertEquals("R S I I* P R", describe(parse("50 30 30 30 30 60")));
    }

    /**
     * Свойства грамматики на случайных заклинаниях: корень есть всегда, у
     * префикса — корень справа, у суффикса — слева, между двумя соседними
     * корнями — суффиксы, не больше одного интерфикса, префиксы, именно в
     * таком порядке.
     */
    @Test
    void propertiesHoldOnRandomSpells() {
        Random random = new Random(42);
        for (int attempt = 0; attempt < 20000; attempt++) {
            int length = 1 + random.nextInt(10);
            int[] orders = new int[length];
            for (int i = 0; i < length; i++) {
                orders[i] = 1 + random.nextInt(4);
            }
            Role[] roles = Grammar.roles(orders);
            boolean[] collapsed = Grammar.collapsed(roles);
            String context = Arrays.toString(orders) + " → " + describe(orders);

            assertTrue(Arrays.asList(roles).contains(Role.ROOT), "нет корня: " + context);
            List<Integer> roots = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                if (roles[i] == Role.ROOT && !collapsed[i]) {
                    roots.add(i);
                }
            }
            for (int i = 0; i < length; i++) {
                final int at = i;
                if (roles[i] == Role.PREFIX) {
                    assertTrue(roots.stream().anyMatch(r -> r > at), "префикс без корня справа: " + context);
                }
                if (roles[i] == Role.SUFFIX) {
                    assertTrue(roots.stream().anyMatch(r -> r < at), "суффикс без корня слева: " + context);
                }
            }
            for (int k = 0; k + 1 < roots.size(); k++) {
                String between = java.util.stream.IntStream.range(roots.get(k) + 1, roots.get(k + 1))
                        .filter(i -> !collapsed[i])
                        .mapToObj(i -> String.valueOf(roles[i].name().charAt(0)))
                        .collect(Collectors.joining());
                assertTrue(between.matches("S*I?P*"), "между корнями «" + between + "»: " + context);
            }
            Program<Integer> program = Program.link(
                    java.util.stream.IntStream.range(0, length).boxed().toList(), roles, collapsed);
            assertEquals(roots.size(), program.steps().size(), context);
        }
    }
}
