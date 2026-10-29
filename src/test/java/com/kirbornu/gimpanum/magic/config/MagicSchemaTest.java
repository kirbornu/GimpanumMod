package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.magic.core.Rune;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JSON Schema трёх файлов знает ровно те поля, что принимает разбор.
 *
 * <p>Схемы собирает {@code tools/magic_schemas.py}, а не код, — этот тест их
 * связывает: добавили поле в разбор и забыли в скрипте — упадёт. Поведения
 * {@code arcanes.json} сверяет игровой тест: библиотека поведений живёт в игре.
 */
class MagicSchemaTest {

    private static JsonObject schema(String name) {
        try (Reader reader = new InputStreamReader(
                MagicSchemaTest.class.getResourceAsStream("/data/gimpanum/magic/" + name + ".schema.json"),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception failure) {
            throw new AssertionError("не читается схема " + name, failure);
        }
    }

    private static Set<String> keys(JsonObject object) {
        return new TreeSet<>(object.getAsJsonObject("properties").keySet());
    }

    private static Set<String> sorted(Set<String> set) {
        return new TreeSet<>(set);
    }

    @Test
    void magicSchemaMatchesParser() {
        JsonObject magic = schema("magic");
        JsonObject properties = magic.getAsJsonObject("properties");
        assertEquals(sorted(MagicConfigParser.MAGIC_KEYS), keys(magic));
        assertEquals(sorted(MagicConfigParser.COST_KEYS), keys(properties.getAsJsonObject("cost")));
        assertEquals(sorted(MagicConfigParser.LIMITS_KEYS), keys(properties.getAsJsonObject("limits")));
        assertEquals(sorted(MagicConfigParser.HOMING_KEYS), keys(properties.getAsJsonObject("homing")));
        assertEquals(Arrays.stream(Rune.values()).map(Rune::id).collect(Collectors.toCollection(TreeSet::new)),
                keys(properties.getAsJsonObject("runes")));
    }

    @Test
    void fieldsSchemaMatchesParser() {
        JsonObject fields = schema("fields");
        assertEquals(sorted(MagicConfigParser.FIELDS_KEYS), keys(fields));
        JsonObject set = fields.getAsJsonObject("properties").getAsJsonObject("sets")
                .getAsJsonObject("additionalProperties");
        assertEquals(sorted(MagicConfigParser.SET_KEYS), keys(set));
        JsonObject field = set.getAsJsonObject("properties").getAsJsonObject("fields").getAsJsonObject("items");
        assertEquals(sorted(MagicConfigParser.FIELD_KEYS), keys(field));
    }

    @Test
    void arcanesSchemaMatchesParser() {
        JsonObject arcanes = schema("arcanes");
        assertEquals(sorted(MagicConfigParser.ARCANES_KEYS), keys(arcanes));
        assertEquals(sorted(MagicConfigParser.ARCANE_KEYS),
                keys(arcanes.getAsJsonObject("definitions").getAsJsonObject("arcane")));
    }
}
