package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kirbornu.gimpanum.magic.core.ArcaneField;
import com.kirbornu.gimpanum.magic.core.FieldSet;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.core.Rune;
import com.kirbornu.gimpanum.magic.core.TurtleSettings;
import com.kirbornu.gimpanum.magic.core.Vector3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Разбор трёх файлов магии в одно целое.
 *
 * <p>Только устройство файлов и связи между ними: есть ли поле, того ли оно
 * вида, существует ли аркан, на который ссылается поле. Что значат параметры
 * поведений и частиц, решает игра — здесь их не проверить без реестров
 * Minecraft. Так разбор остаётся чистым кодом, и формат проверяется теми же
 * тестами, что и математика.
 */
public final class MagicConfigParser {

    public static final String ARCANES = "arcanes.json";
    public static final String FIELDS = "fields.json";
    public static final String MAGIC = "magic.json";

    /** Самый длинный id аркана — чтобы опечатка в файле не превратилась в абзац в чате. */
    private static final int MAX_ID_LENGTH = 64;

    private static final Set<String> ROLE_KEYS = Set.of("root", "prefix", "suffix", "interfix");

    /**
     * Ссылка на JSON Schema для редактора. Моду она не нужна, поэтому в
     * корне любого из трёх файлов пропускается молча.
     */
    private static final String SCHEMA_KEY = "$schema";

    /*
     * Допустимые поля каждого объекта трёх файлов. Вынесены сюда, чтобы
     * модульный тест сверял с ними JSON Schema: схема собирается скриптом
     * отдельно и иначе разошлась бы с разбором незаметно.
     */
    public static final Set<String> MAGIC_KEYS = Set.of(SCHEMA_KEY, "runes", "turnAngle", "stepMultiplier", "cost",
            "cooldownSeconds", "tabletUpgradeItem", "limits", "homing");
    public static final Set<String> COST_KEYS = Set.of("foodPerRune", "canKill", "healthPerFood", "crystalItem",
            "runesPerCrystal");
    public static final Set<String> LIMITS_KEYS = Set.of("blocksPerSpell", "entitiesPerSpell",
            "entityLifetimeSeconds", "homingEntities", "particlesPerTick", "defaultTargetRadius");
    public static final Set<String> HOMING_KEYS = Set.of("searchIntervalTicks", "searchRadius");
    public static final Set<String> ARCANES_KEYS = Set.of(SCHEMA_KEY, "arcanes");
    public static final Set<String> ARCANE_KEYS = Set.of("id", "name", "order", "root", "prefix", "suffix",
            "interfix", "particles", "sounds");
    public static final Set<String> FIELDS_KEYS = Set.of(SCHEMA_KEY, "sets");
    public static final Set<String> SET_KEYS = Set.of("catalysts", "turnAngle", "stepMultiplier", "fields");
    public static final Set<String> FIELD_KEYS = Set.of("arcane", "center", "radius");

    /** Чем платят хрусталём, если в старом {@code magic.json} этого не сказано. */
    public static final String DEFAULT_CRYSTAL = "gimpanum:crystal_shard";
    /** Сколько рун на осколок, если в старом {@code magic.json} этого не сказано. */
    public static final int DEFAULT_RUNES_PER_CRYSTAL = 4;

    /** Всё, что прочитано из трёх файлов. */
    public record Parsed(MagicSettings settings, Map<String, ArcaneDef> arcanes, FieldsConfig fields) {
    }

    private MagicConfigParser() {
    }

    /** @return разобранное или {@code null}, если есть хоть одна ошибка */
    public static Parsed parse(JsonElement arcanes, JsonElement fields, JsonElement magic, ConfigErrors errors) {
        Parsed parsed = parsePartially(arcanes, fields, magic, errors);
        return errors.isEmpty() && parsed.settings() != null && parsed.arcanes() != null && parsed.fields() != null
                ? parsed : null;
    }

    /**
     * Разбор, который не бросает дело на первой ошибке: всё, что удалось
     * прочитать, возвращается, даже если ошибки есть. Нужно игре, чтобы в том
     * же проходе проверить поведения уцелевших арканов и показать все ошибки
     * разом, а не по одной за перезагрузку.
     *
     * @return части могут быть {@code null}, если файл не прочитался совсем
     */
    public static Parsed parsePartially(JsonElement arcanes, JsonElement fields, JsonElement magic,
                                        ConfigErrors errors) {
        MagicSettings settings = settings(root(magic, MAGIC, errors));
        Set<String> ids = new java.util.HashSet<>();
        Map<String, ArcaneDef> arcaneDefs = arcanes(root(arcanes, ARCANES, errors), ids);
        FieldsConfig fieldsConfig = fields(root(fields, FIELDS, errors), arcaneDefs == null ? null : ids);
        return new Parsed(settings, arcaneDefs, fieldsConfig);
    }

    private static Json root(JsonElement element, String file, ConfigErrors errors) {
        if (element instanceof JsonObject object) {
            return new Json(object, file, errors);
        }
        errors.add(file, "файл должен быть объектом {…}");
        return null;
    }

    // --- magic.json -------------------------------------------------------

    private static MagicSettings settings(Json json) {
        if (json == null) {
            return null;
        }
        json.rejectUnknown(MAGIC_KEYS);

        List<Vector3> vectors = new ArrayList<>();
        Json runes = json.object("runes");
        if (runes != null) {
            for (Rune rune : Rune.values()) {
                double[] v = runes.has(rune.id()) ? runes.triple(rune.id()) : null;
                if (!runes.has(rune.id())) {
                    runes.errors().add(runes.path(rune.id()), "нет вектора руны " + rune.title());
                }
                vectors.add(v == null ? Vector3.ZERO : new Vector3(v[0], v[1], v[2]));
            }
            for (String key : runes.raw().keySet()) {
                if (Rune.byId(key) == null) {
                    runes.errors().add(runes.path(key), "такой руны нет");
                }
            }
        }
        Double turnAngle = json.number("turnAngle");
        Double stepMultiplier = json.number("stepMultiplier", 0.0, 1000.0);

        MagicSettings.Cost cost = null;
        Json costJson = json.object("cost");
        if (costJson != null) {
            costJson.rejectUnknown(COST_KEYS);
            Double food = costJson.number("foodPerRune", 0.0, 1000.0);
            Boolean canKill = costJson.bool("canKill");
            Double health = costJson.number("healthPerFood", 0.0, 1000.0);
            // Хрусталь появился в цене позже еды, поэтому эти два поля
            // необязательны: старый magic.json без них обязан читаться, а не
            // ронять сервер на образец. Без них — значения образца.
            String crystal = costJson.has("crystalItem") ? costJson.string("crystalItem") : DEFAULT_CRYSTAL;
            Integer perCrystal = costJson.has("runesPerCrystal")
                    ? costJson.integer("runesPerCrystal", 0, 1000) : DEFAULT_RUNES_PER_CRYSTAL;
            if (food != null && canKill != null && health != null && crystal != null && perCrystal != null) {
                cost = new MagicSettings.Cost(food, canKill, health, crystal, perCrystal);
            }
        }
        Double cooldown = json.number("cooldownSeconds", 0.0, 3600.0);
        String upgrade = json.string("tabletUpgradeItem");

        MagicSettings.Limits limits = null;
        Json limitsJson = json.object("limits");
        if (limitsJson != null) {
            limitsJson.rejectUnknown(LIMITS_KEYS);
            Integer blocks = limitsJson.integer("blocksPerSpell", 0, 100_000);
            Integer entities = limitsJson.integer("entitiesPerSpell", 1, 1000);
            Double lifetime = limitsJson.number("entityLifetimeSeconds", 1.0, 3600.0);
            Integer homing = limitsJson.integer("homingEntities", 0, 10_000);
            Integer particles = limitsJson.integer("particlesPerTick", 0, 100_000);
            Double radius = limitsJson.number("defaultTargetRadius", 0.0, 256.0);
            if (blocks != null && entities != null && lifetime != null && homing != null && particles != null
                    && radius != null) {
                limits = new MagicSettings.Limits(blocks, entities, lifetime, homing, particles, radius);
            }
        }

        MagicSettings.Homing homing = null;
        Json homingJson = json.object("homing");
        if (homingJson != null) {
            homingJson.rejectUnknown(HOMING_KEYS);
            Integer interval = homingJson.integer("searchIntervalTicks", 1, 1200);
            Double radius = homingJson.number("searchRadius", 0.0, 256.0);
            if (interval != null && radius != null) {
                homing = new MagicSettings.Homing(interval, radius);
            }
        }

        if (runes == null || turnAngle == null || stepMultiplier == null || cost == null || cooldown == null
                || upgrade == null || limits == null || homing == null) {
            return null;
        }
        return new MagicSettings(new TurtleSettings(vectors, turnAngle, stepMultiplier), cost, cooldown, upgrade,
                limits, homing);
    }

    // --- arcanes.json -----------------------------------------------------

    /** @param ids сюда пишутся id всех арканов, даже испорченных, — чтобы поля не ссылались на них зря */
    private static Map<String, ArcaneDef> arcanes(Json json, Set<String> ids) {
        if (json == null) {
            return null;
        }
        json.rejectUnknown(ARCANES_KEYS);
        JsonArray list = json.array("arcanes");
        if (list == null) {
            return null;
        }
        Map<String, ArcaneDef> out = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            String path = json.path("arcanes") + "[" + i + "]";
            if (!(list.get(i) instanceof JsonObject object)) {
                json.errors().add(path, "аркан должен быть объектом {…}");
                continue;
            }
            Json arcane = new Json(object, path, json.errors());
            if (arcane.raw().get("id") instanceof com.google.gson.JsonPrimitive id && id.isString()) {
                ids.add(id.getAsString());
            }
            ArcaneDef def = arcane(arcane);
            if (def == null) {
                continue;
            }
            if (out.containsKey(def.id())) {
                json.errors().add(path + ".id", "аркан «" + def.id() + "» уже описан выше");
                continue;
            }
            out.put(def.id(), def);
        }
        if (out.isEmpty() && json.errors().isEmpty()) {
            json.errors().add(json.path("arcanes"), "нет ни одного аркана");
        }
        return out;
    }

    private static ArcaneDef arcane(Json json) {
        json.rejectUnknown(ARCANE_KEYS);
        String id = json.string("id");
        if (id != null && !validId(id)) {
            json.errors().add(json.path("id"), "id — латиница в нижнем регистре, цифры и «_», не длиннее "
                    + MAX_ID_LENGTH + " символов");
            id = null;
        }
        String name = json.string("name");
        // Номер строго положителен: края заклинания — нули, и аркан с нулём
        // ломал бы гарантию, что первый и последний арканы выше края.
        Integer order = json.integer("order", 1, Integer.MAX_VALUE);
        Map<Role, List<BehaviorSpec>> behaviors = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            behaviors.put(role, behaviorList(json, role.key()));
        }
        Json particles = json.has("particles") ? json.object("particles") : null;
        if (particles != null) {
            particles.rejectUnknown(ROLE_KEYS);
        }
        Json sounds = json.has("sounds") ? json.object("sounds") : null;
        if (sounds != null) {
            sounds.rejectUnknown(ROLE_KEYS);
        }
        if (id == null) {
            return null;
        }
        // С испорченными именем или номером аркан всё равно идёт дальше —
        // ошибка уже записана, и применено ничего не будет, — чтобы игра
        // проверила и его поведения и показала их ошибки в тот же раз.
        return new ArcaneDef(id, name != null ? name : id, order != null ? order : 1, behaviors, particles, sounds);
    }

    private static List<BehaviorSpec> behaviorList(Json arcane, String role) {
        if (!arcane.has(role)) {
            // Пустая роль допустима: аркан, который, скажем, ничего не делает
            // интерфиксом, просто пропускает паузу между корнями.
            return List.of();
        }
        JsonArray list = arcane.array(role);
        if (list == null) {
            return List.of();
        }
        List<BehaviorSpec> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String path = arcane.path(role) + "[" + i + "]";
            JsonElement element = list.get(i);
            if (!(element instanceof JsonObject object)) {
                arcane.errors().add(path, "поведение должно быть объектом {\"type\": …}");
                continue;
            }
            Json behavior = new Json(object, path, arcane.errors());
            String type = behavior.string("type");
            if (type != null) {
                out.add(new BehaviorSpec(type, behavior));
            }
        }
        return List.copyOf(out);
    }

    private static boolean validId(String id) {
        return id.length() <= MAX_ID_LENGTH && id.matches("[a-z0-9_]+");
    }

    // --- fields.json ------------------------------------------------------

    /** @param arcanes id известных арканов или {@code null}, если их файл не прочитался */
    private static FieldsConfig fields(Json json, Set<String> arcanes) {
        if (json == null) {
            return null;
        }
        json.rejectUnknown(FIELDS_KEYS);
        Json sets = json.object("sets");
        if (sets == null) {
            return null;
        }
        Map<String, FieldSet> out = new LinkedHashMap<>();
        Map<String, String> catalysts = new LinkedHashMap<>();
        for (String name : sets.raw().keySet()) {
            Json set = sets.object(name);
            if (set == null) {
                continue;
            }
            set.rejectUnknown(SET_KEYS);
            if (set.has("catalysts")) {
                JsonArray list = set.array("catalysts");
                for (int i = 0; list != null && i < list.size(); i++) {
                    String path = set.path("catalysts") + "[" + i + "]";
                    JsonElement item = list.get(i);
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        set.errors().add(path, "ожидался id предмета строкой");
                        continue;
                    }
                    String previous = catalysts.putIfAbsent(item.getAsString(), name);
                    if (previous != null) {
                        set.errors().add(path, item.getAsString() + " уже привязан к набору «" + previous + "»");
                    }
                }
            }
            Double turnAngle = set.optionalNumber("turnAngle");
            Double stepMultiplier = set.has("stepMultiplier") ? set.number("stepMultiplier", 0.0, 1000.0) : null;
            List<ArcaneField> fields = new ArrayList<>();
            JsonArray list = set.array("fields");
            for (int i = 0; list != null && i < list.size(); i++) {
                String path = set.path("fields") + "[" + i + "]";
                if (!(list.get(i) instanceof JsonObject object)) {
                    set.errors().add(path, "поле должно быть объектом {\"arcane\", \"center\", \"radius\"}");
                    continue;
                }
                Json field = new Json(object, path, set.errors());
                field.rejectUnknown(FIELD_KEYS);
                String arcane = field.string("arcane");
                if (arcane != null && arcanes != null && !arcanes.contains(arcane)) {
                    field.errors().add(field.path("arcane"), "аркана «" + arcane + "» нет в " + ARCANES);
                    arcane = null;
                }
                double[] center = field.triple("center");
                Double radius = field.number("radius", 0.0, 1.0e6);
                if (radius != null && radius <= 0) {
                    field.errors().add(field.path("radius"), "радиус должен быть больше нуля");
                    radius = null;
                }
                if (arcane != null && center != null && radius != null) {
                    fields.add(new ArcaneField(arcane, new Vector3(center[0], center[1], center[2]), radius));
                }
            }
            out.put(name, new FieldSet(name, fields, turnAngle, stepMultiplier));
        }
        FieldSet fallback = out.get(FieldsConfig.DEFAULT);
        if (fallback == null) {
            sets.errors().add(sets.path(FieldsConfig.DEFAULT), "нет набора по умолчанию");
        } else if (fallback.isEmpty()) {
            sets.errors().add(sets.path(FieldsConfig.DEFAULT),
                    "набор по умолчанию пуст — без полей ни одна рунопись не получит аркан");
        }
        return new FieldsConfig(out, catalysts);
    }
}
