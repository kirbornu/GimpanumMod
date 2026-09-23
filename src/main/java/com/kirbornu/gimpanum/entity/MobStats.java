package com.kirbornu.gimpanum.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Характеристики обитателей Гимпанума — из файла настроек.
 *
 * <p>Файл {@code config/gimpanum/mobs.json} рядом с остальными настройками мода
 * и перечитывается тем же {@code /gimpanum config reload}. Формат — плоский, по
 * разделу на моба: смысл каждого числа виден из имени, и заводить ради этого
 * датапак незачем — это то, что владелец сервера правит на ходу, подбирая
 * ощущение от боя.
 *
 * <p><b>Встроенные значения — это образец из джарки</b>, а не числа в коде. Он
 * же ложится в папку настроек при первом запуске, он же служит основой, поверх
 * которой читается файл. Так число живёт в одном месте: раньше оно
 * повторялось трижды — в атрибутах сущности, в таблице здесь и в образце, — и
 * правка в одном из них молча расходилась с двумя другими. Образец есть и у
 * клиента выделенного сервера, который файла настроек не видит, а моба всё
 * равно создаёт.
 *
 * <p>Файл сливается с образцом по полю, а не подменяет его. Пропущенное поле
 * берёт встроенное значение, испорченное — тоже, с записью в журнал. Так одна
 * опечатка не обнуляет весь файл и не оставляет измерение без мобов: настройка
 * боя не должна уметь ломать игру. Поля, которых в образце нет, не читаются
 * вовсе — это почти всегда опечатка, и о ней тоже пишется в журнал.
 *
 * <p><b>Что можно менять на живом сервере, а что нет.</b> Числа-атрибуты —
 * здоровье, урон, броня, скорость, чутьё — применяются каждому мобу при
 * появлении в мире и переназначаются всем уже стоящим при перечитывании. А вот
 * память и темп удара уходят внутрь целей поведения в момент создания моба, и
 * заменить их у живого нельзя: они подействуют на тех, кто появится после
 * перечитывания.
 */
public final class MobStats {

    /**
     * Раздел файла — числа одного моба или одной общей настройки.
     *
     * <p>Поле, которого нет в образце, — ошибка в коде, а не в настройке,
     * поэтому здесь оно падает сразу, а не подставляет ноль.
     */
    public record Section(String name, JsonObject json) {

        public double number(String field) {
            return get(field).getAsDouble();
        }

        public int integer(String field) {
            return get(field).getAsInt();
        }

        public boolean has(String field) {
            return json.has(field);
        }

        public JsonArray list(String field) {
            return get(field).getAsJsonArray();
        }

        private JsonElement get(String field) {
            JsonElement value = json.get(field);
            if (value == null) {
                throw new IllegalArgumentException("в образце нет поля " + name + "." + field);
            }
            return value;
        }
    }

    private static final String FILE = "mobs.json";
    private static final String DEFAULTS = "/data/gimpanum/entity/default_mobs.json";

    /** Какое поле какой атрибут задаёт. Поля нет у моба — атрибут не трогаем. */
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = Map.of(
            "health", Attributes.MAX_HEALTH,
            "damage", Attributes.ATTACK_DAMAGE,
            "armor", Attributes.ARMOR,
            "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE,
            "movement_speed", Attributes.MOVEMENT_SPEED,
            "flying_speed", Attributes.FLYING_SPEED,
            "detection_blocks", Attributes.FOLLOW_RANGE);

    private static final JsonObject BUILT_IN = readBuiltIn();

    private static JsonObject loaded = BUILT_IN;

    private MobStats() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve(FILE);
    }

    /** Сколько разделов прочитано — мобов и общих настроек. */
    public static int count() {
        return sections().size();
    }

    /** Имена всех разделов — мобов и общих настроек. */
    public static List<String> sections() {
        return loaded.keySet().stream().filter(key -> !key.startsWith("_")).toList();
    }

    /** Числа из файла для раздела, например {@code dune_walker} или {@code spawner}. */
    public static Section of(String name) {
        return new Section(name, loaded.getAsJsonObject(name));
    }

    /**
     * Числа из образца, без файла настроек.
     *
     * <p>Для {@code createAttributes}: реестр атрибутов собирается при запуске
     * игры, задолго до того, как появится сервер и его папка настроек.
     */
    public static Section builtIn(String name) {
        return new Section(name, BUILT_IN.getAsJsonObject(name));
    }

    /** Атрибуты из образца — основа, на которую потом ляжет файл. */
    public static AttributeSupplier.Builder attributes(AttributeSupplier.Builder builder, String name) {
        Section stats = builtIn(name);
        ATTRIBUTES.forEach((field, attribute) -> {
            if (stats.has(field)) {
                builder.add(attribute, stats.number(field));
            }
        });
        return builder;
    }

    public static void load() {
        Path file = path();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream source = open()) {
                    Files.copy(source, file);
                }
                Gimpanum.LOGGER.info("Создан пример характеристик мобов: {}", file);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    throw new IOException("в файле не объект");
                }
                loaded = merge("", BUILT_IN, root.getAsJsonObject());
            }
            Gimpanum.LOGGER.info("Характеристики мобов прочитаны: {}", file);
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Не прочитались характеристики мобов {} — беру встроенные", file, failure);
            loaded = BUILT_IN;
        }
        MobLoot.check();
    }

    /**
     * Образец, поверх которого лежат поля из файла.
     *
     * <p>Разделы сливаются вглубь, списки берутся из файла целиком: список
     * добычи — это одно решение, а не набор полей, которые стоит дополнять.
     */
    private static JsonObject merge(String path, JsonObject base, JsonObject mine) {
        JsonObject out = new JsonObject();
        for (Entry<String, JsonElement> entry : base.entrySet()) {
            String key = entry.getKey();
            JsonElement fallback = entry.getValue();
            JsonElement value = mine.get(key);
            if (value == null || key.startsWith("_")) {
                out.add(key, fallback);
            } else if (fallback.isJsonObject() && value.isJsonObject()) {
                out.add(key, merge(path + key + ".", fallback.getAsJsonObject(), value.getAsJsonObject()));
            } else if (sameKind(fallback, value)) {
                out.add(key, value);
            } else {
                Gimpanum.LOGGER.warn("{}{} — не того вида, беру {}", path, key, fallback);
                out.add(key, fallback);
            }
        }
        for (String key : mine.keySet()) {
            if (!base.has(key)) {
                Gimpanum.LOGGER.warn("{}{} — такого поля нет, пропускаю", path, key);
            }
        }
        return out;
    }

    private static boolean sameKind(JsonElement a, JsonElement b) {
        if (a.isJsonArray() || b.isJsonArray()) {
            return a.isJsonArray() && b.isJsonArray();
        }
        if (!a.isJsonPrimitive() || !b.isJsonPrimitive()) {
            return false;
        }
        return a.getAsJsonPrimitive().isNumber() == b.getAsJsonPrimitive().isNumber()
                && a.getAsJsonPrimitive().isBoolean() == b.getAsJsonPrimitive().isBoolean();
    }

    private static JsonObject readBuiltIn() {
        try (Reader reader = new InputStreamReader(open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("образец " + DEFAULTS + " не читается", failure);
        }
    }

    private static InputStream open() throws IOException {
        InputStream source = MobStats.class.getResourceAsStream(DEFAULTS);
        if (source == null) {
            throw new IOException("образец " + DEFAULTS + " не найден в джарке");
        }
        return source;
    }

    /**
     * Назначить мобу его числа.
     *
     * <p>Поверх атрибутов из {@code createAttributes}, каждому мобу при
     * появлении в мире: там числа из образца, здесь — из файла.
     */
    public static void apply(LivingEntity entity) {
        String name = key(entity.getType());
        if (name == null) {
            return;
        }
        Section stats = of(name);
        boolean full = entity.getHealth() >= entity.getMaxHealth();
        ATTRIBUTES.forEach((field, attribute) -> {
            if (stats.has(field)) {
                set(entity, attribute, stats.number(field));
            }
        });
        // Тому, кто был цел, поднимаем здоровье до нового предела: иначе
        // прибавка к здоровью в настройке ничего не давала бы до перерождения.
        if (full) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static void set(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Переназначить числа всем нашим мобам, что сейчас в памяти.
     *
     * <p>Нужно после перечитывания файла: без этого правка дошла бы только до
     * тех, кто появится потом, и подбирать ощущение от боя пришлось бы, убивая
     * старых.
     */
    public static void applyToLoaded(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && key(entity.getType()) != null) {
                    apply(living);
                }
            }
        }
    }

    /** Раздел моба по пути вида в реестре, если это наш моб и раздел у него есть. */
    @Nullable
    public static String key(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return Gimpanum.MOD_ID.equals(id.getNamespace()) && BUILT_IN.has(id.getPath()) ? id.getPath() : null;
    }
}
