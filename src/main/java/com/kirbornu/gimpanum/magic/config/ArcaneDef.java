package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonObject;
import com.kirbornu.gimpanum.magic.core.Role;

import java.util.List;
import java.util.Map;

/**
 * Аркан из {@code arcanes.json} до того, как игра превратила его поведения в код.
 *
 * @param behaviors списки поведений для каждой из четырёх ролей
 * @param particles описание частиц по ролям или {@code null}
 */
public record ArcaneDef(String id, String name, int order, Map<Role, List<BehaviorSpec>> behaviors,
                        Json particles) {

    public List<BehaviorSpec> behaviors(Role role) {
        return behaviors.getOrDefault(role, List.of());
    }

    public JsonObject particlesJson() {
        return particles == null ? null : particles.raw();
    }
}
