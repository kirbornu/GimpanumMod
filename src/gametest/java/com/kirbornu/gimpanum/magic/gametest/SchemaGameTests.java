package com.kirbornu.gimpanum.magic.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.behavior.BehaviorLibrary;
import com.kirbornu.gimpanum.magic.behavior.BehaviorType;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * JSON Schema для {@code arcanes.json} не разошлась с библиотекой поведений.
 *
 * <p>Схему собирает {@code tools/magic_schemas.py} по своей таблице, а не по
 * коду, — этот тест и есть то, что их связывает: добавили поведение и забыли
 * пересобрать схему — тест упадёт и скажет, что поправить.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class SchemaGameTests {

    static JsonObject schema() {
        try (Reader reader = new InputStreamReader(
                MagicConfig.class.getResourceAsStream("/data/gimpanum/magic/" + MagicConfig.SCHEMA),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception failure) {
            throw new AssertionError("схема не читается", failure);
        }
    }

    static Set<String> enumOf(JsonObject definitions, String name) {
        JsonArray values = definitions.getAsJsonObject(name).getAsJsonObject("properties")
                .getAsJsonObject("type").getAsJsonArray("enum");
        Set<String> out = new TreeSet<>();
        for (JsonElement value : values) {
            out.add(value.getAsString());
        }
        return out;
    }

    @GameTest(template = "magictest")
    public static void schemaMatchesBehaviorLibrary(GameTestHelper h) {
        JsonObject definitions = schema().getAsJsonObject("definitions");
        Set<String> roleTypes = new TreeSet<>();
        Set<String> processTypes = new TreeSet<>();
        Set<String> problems = new TreeSet<>();
        for (BehaviorType type : BehaviorLibrary.types()) {
            (type.kind() == Behavior.Kind.PROCESS ? processTypes : roleTypes).add(type.name());
            JsonObject params = definitions.getAsJsonObject("params_" + type.name());
            if (params == null) {
                problems.add(type.name() + ": нет в схеме");
                continue;
            }
            Set<String> inSchema = new HashSet<>(params.getAsJsonObject("properties").keySet());
            inSchema.remove("type");
            if (!inSchema.equals(type.params())) {
                problems.add(type.name() + ": параметры в схеме " + new TreeSet<>(inSchema) + ", в коде "
                        + new TreeSet<>(type.params()));
            }
        }
        Set<String> extra = definitions.keySet().stream().filter(k -> k.startsWith("params_"))
                .map(k -> k.substring("params_".length()))
                .filter(n -> !roleTypes.contains(n) && !processTypes.contains(n))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!extra.isEmpty()) {
            problems.add("в схеме лишние: " + extra);
        }
        if (!enumOf(definitions, "roleBehavior").equals(roleTypes)) {
            problems.add("roleBehavior: " + enumOf(definitions, "roleBehavior") + " ≠ " + roleTypes);
        }
        if (!enumOf(definitions, "interfixBehavior").equals(processTypes)) {
            problems.add("interfixBehavior: " + enumOf(definitions, "interfixBehavior") + " ≠ " + processTypes);
        }
        Set<String> aura = new TreeSet<>(roleTypes);
        BehaviorLibrary.types().stream().filter(t -> t.kind() != Behavior.Kind.ACTION).forEach(t -> aura.remove(t.name()));
        aura.remove("echo_root");
        if (!enumOf(definitions, "auraAction").equals(aura)) {
            problems.add("auraAction: " + enumOf(definitions, "auraAction") + " ≠ " + aura);
        }
        MagicTestKit.log("schema: " + (roleTypes.size() + processTypes.size()) + " поведений, расхождений "
                + problems.size());
        h.assertTrue(problems.isEmpty(), "схема разошлась с кодом (пересоберите tools/magic_schemas.py): "
                + problems);
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void schemaIsWrittenNextToConfig(GameTestHelper h) {
        h.assertTrue(MagicConfig.load(h.getLevel().getServer()).isEmpty(), "перезагрузка");
        for (String name : MagicConfig.SCHEMAS) {
            h.assertTrue(Files.isRegularFile(MagicConfig.dir().resolve(name)), name + " нет рядом с файлами");
        }
        h.succeed();
    }
}
