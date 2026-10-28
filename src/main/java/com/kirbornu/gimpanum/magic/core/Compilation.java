package com.kirbornu.gimpanum.magic.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Полный разбор заклинания: точка и аркан каждой рунописи, роли, схлопывания
 * и итоговая программа.
 *
 * <p>Промежуточные шаги хранятся не ради игры, а ради отладки: когда
 * заклинание ведёт себя не так, как в песочнице, первым делом смотрят, на
 * каком из шагов разошлись числа.
 */
public record Compilation(List<Vector3> points, List<String> arcanes, Role[] roles, boolean[] collapsed,
                          Program<String> program) {

    /**
     * @param runescripts рунописи заклинания, каждая — индексы рун
     * @param order       порядковый номер аркана по его id
     */
    public static Compilation of(List<int[]> runescripts, TurtleSettings settings, FieldSet fields,
                                 ToIntFunction<String> order) {
        TurtleSettings turtle = fields.applyTo(settings);
        List<Vector3> points = new ArrayList<>();
        List<String> arcanes = new ArrayList<>();
        for (int[] runescript : runescripts) {
            Vector3 point = Turtle.walk(runescript, turtle);
            points.add(point);
            arcanes.add(fields.resolve(point));
        }
        int[] orders = arcanes.stream().mapToInt(order).toArray();
        Role[] roles = Grammar.roles(orders);
        boolean[] collapsed = Grammar.collapsed(roles);
        return new Compilation(List.copyOf(points), List.copyOf(arcanes), roles, collapsed,
                Program.link(arcanes, roles, collapsed));
    }
}
