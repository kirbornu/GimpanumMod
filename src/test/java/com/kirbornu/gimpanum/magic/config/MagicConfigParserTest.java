package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.magic.core.Compilation;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.core.Turtle;
import com.kirbornu.gimpanum.magic.core.Vector3;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Файлы по умолчанию из джарки разбираются без ошибок и дают эталонные числа. */
class MagicConfigParserTest {

    private static JsonElement resource(String name) {
        try (Reader reader = new InputStreamReader(
                MagicConfigParserTest.class.getResourceAsStream("/data/gimpanum/magic/" + name),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception failure) {
            throw new AssertionError("не читается " + name, failure);
        }
    }

    private static MagicConfigParser.Parsed parse(JsonElement arcanes, JsonElement fields, JsonElement magic,
                                                  ConfigErrors errors) {
        return MagicConfigParser.parse(arcanes, fields, magic, errors);
    }

    @Test
    void defaultsParseCleanly() {
        ConfigErrors errors = new ConfigErrors();
        MagicConfigParser.Parsed parsed = parse(resource("arcanes.json"), resource("fields.json"),
                resource("magic.json"), errors);
        assertTrue(errors.isEmpty(), () -> String.join("\n", errors.messages()));
        assertNotNull(parsed);
        assertEquals(List.of("fire", "water", "heal", "light"), List.copyOf(parsed.arcanes().keySet()));
        assertEquals(20, parsed.arcanes().get("fire").order());
        assertEquals("echo_root", parsed.arcanes().get("fire").behaviors(Role.SUFFIX).get(0).type());
        assertEquals(6, parsed.fields().sets().get("default").fields().size());
        assertTrue(parsed.fields().sets().get("scalk").isEmpty());
    }

    @Test
    void defaultsReproduceReferencePoints() {
        MagicConfigParser.Parsed parsed = parse(resource("arcanes.json"), resource("fields.json"),
                resource("magic.json"), new ConfigErrors());
        assertNotNull(parsed);
        Vector3 point = Turtle.walk(new int[]{6, 6, 10, 3, 1}, parsed.settings().turtle());
        assertEquals(2.7366, point.x(), 1e-4);
        assertEquals(-1.3692, point.y(), 1e-4);
        assertEquals(-0.3452, point.z(), 1e-4);
        assertEquals("fire", parsed.fields().forCatalyst(null).resolve(point));

        Compilation compilation = Compilation.of(List.of(new int[]{4, 0}, new int[]{0}),
                parsed.settings().turtle(), parsed.fields().forCatalyst(null),
                id -> parsed.arcanes().get(id).order());
        assertEquals(List.of("water", "light"), compilation.arcanes());
    }

    @Test
    void catalystsSelectSets() {
        MagicConfigParser.Parsed parsed = parse(resource("arcanes.json"), resource("fields.json"),
                resource("magic.json"), new ConfigErrors());
        assertNotNull(parsed);
        FieldsConfig fields = parsed.fields();
        assertEquals("default", fields.forCatalyst(null).name());
        assertEquals("default", fields.forCatalyst("gimpanum:crystal_shard").name());
        assertEquals("default", fields.forCatalyst("minecraft:dirt").name());
        // Скалк привязан, но его набор пока пуст — читается как без катализатора.
        assertEquals("default", fields.forCatalyst("minecraft:sculk").name());
    }

    @Test
    void errorsPointAtThePlace() {
        JsonObject arcanes = resource("arcanes.json").getAsJsonObject();
        JsonObject water = arcanes.getAsJsonArray("arcanes").get(1).getAsJsonObject();
        water.remove("order");
        water.addProperty("ordr", 60);
        JsonObject fields = resource("fields.json").getAsJsonObject();
        fields.getAsJsonObject("sets").getAsJsonObject("default").getAsJsonArray("fields").get(0)
                .getAsJsonObject().addProperty("arcane", "lava");
        JsonObject magic = resource("magic.json").getAsJsonObject();
        magic.getAsJsonObject("runes").remove("mar");

        ConfigErrors errors = new ConfigErrors();
        assertNull(parse(arcanes, fields, magic, errors));
        String all = String.join("\n", errors.messages());
        assertTrue(all.contains("arcanes.json.arcanes[1].ordr: неизвестное поле"), all);
        assertTrue(all.contains("arcanes.json.arcanes[1].order: нет обязательного числа"), all);
        assertTrue(all.contains("fields.json.sets.default.fields[0].arcane: аркана «lava» нет"), all);
        assertTrue(all.contains("magic.json.runes.mar: нет вектора руны МАР"), all);
    }

    @Test
    void duplicateArcaneAndBadOrder() {
        JsonObject arcanes = resource("arcanes.json").getAsJsonObject();
        JsonObject copy = arcanes.getAsJsonArray("arcanes").get(0).getAsJsonObject().deepCopy();
        copy.addProperty("order", 0);
        arcanes.getAsJsonArray("arcanes").add(copy);
        ConfigErrors errors = new ConfigErrors();
        assertNull(parse(arcanes, resource("fields.json"), resource("magic.json"), errors));
        String all = String.join("\n", errors.messages());
        assertTrue(all.contains("arcanes.json.arcanes[4].order: должно быть от 1"), all);
    }

    @Test
    void sharedCatalystIsAnError() {
        JsonObject fields = resource("fields.json").getAsJsonObject();
        fields.getAsJsonObject("sets").getAsJsonObject("citrine").getAsJsonArray("catalysts")
                .add("minecraft:sculk");
        ConfigErrors errors = new ConfigErrors();
        assertNull(parse(resource("arcanes.json"), fields, resource("magic.json"), errors));
        assertTrue(String.join("\n", errors.messages()).contains("уже привязан к набору «scalk»"));
    }
}
