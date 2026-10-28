package com.kirbornu.gimpanum.magic.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramTest {

    private static Program<String> link(String... spell) {
        List<String> arcanes = List.of(spell);
        int[] orders = arcanes.stream().mapToInt(Defaults.ORDERS::get).toArray();
        Role[] roles = Grammar.roles(orders);
        return Program.link(arcanes, roles, Grammar.collapsed(roles));
    }

    @Test
    void prefixRootInterfixRoot() {
        // 20 70 60 90: префикс, корень, интерфикс, корень.
        Program<String> program = link("fire", "heal", "water", "light");
        assertEquals(List.of(
                new Program.Step<>("heal", List.of("fire"), List.of(), Optional.of("water")),
                new Program.Step<>("light", List.of(), List.of(), Optional.empty())), program.steps());
    }

    @Test
    void rootSuffix() {
        Program<String> program = link("heal", "fire");
        assertEquals(List.of(new Program.Step<>("heal", List.of(), List.of("fire"), Optional.empty())),
                program.steps());
    }

    @Test
    void severalPrefixesAndSuffixesKeepOrder() {
        // 20 60 90 70 60 20: префикс, префикс, корень, суффикс, суффикс, суффикс.
        Program<String> program = link("fire", "water", "light", "heal", "water", "fire");
        assertEquals(List.of(new Program.Step<>("light", List.of("fire", "water"),
                List.of("heal", "water", "fire"), Optional.empty())), program.steps());
    }

    @Test
    void collapsedRootIsIgnoredEntirely() {
        // 70 70 20: корень, корень (сх.), суффикс — суффикс уходит к первому корню.
        Program<String> program = link("heal", "heal", "fire");
        assertEquals(List.of(new Program.Step<>("heal", List.of(), List.of("fire"), Optional.empty())),
                program.steps());
    }

    @Test
    void endToEndCompilation() {
        // ДАР·АН → water (60), АН → light (90): префикс? 60 < 90 справа → префикс, light — корень.
        Compilation compilation = Compilation.of(List.of(new int[]{4, 0}, new int[]{0}),
                Defaults.TURTLE, Defaults.FIELDS, Defaults.ORDERS::get);
        assertEquals(List.of("water", "light"), compilation.arcanes());
        assertEquals(List.of(new Program.Step<>("light", List.of("water"), List.of(), Optional.empty())),
                compilation.program().steps());
    }
}
