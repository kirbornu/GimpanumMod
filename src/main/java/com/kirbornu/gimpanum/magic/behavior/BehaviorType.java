package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.config.Json;

import java.util.Set;
import java.util.function.Function;

/**
 * Тип поведения: имя в файле, вид, допустимые параметры и сборка из них.
 *
 * @param params  имена параметров, кроме {@code type}; остальные — ошибка
 * @param factory сборка из параметров; ошибки пишет в {@link Json#errors()} и
 *                тогда может вернуть {@code null}
 */
public record BehaviorType(String name, Behavior.Kind kind, Set<String> params, Function<Json, Behavior> factory) {
}
