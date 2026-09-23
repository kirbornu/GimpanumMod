package com.kirbornu.gimpanum.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Характеристики обитателей Гимпанума — из файла настроек.
 *
 * <p>Файл {@code config/gimpanum/mobs.json} рядом с остальными настройками мода
 * и перечитывается тем же {@code /gimpanum config reload}. Формат — плоский, по
 * разделу на моба: смысл каждого числа виден из имени, и заводить ради этого
 * датапак незачем — это то, что владелец сервера правит на ходу, подбирая
 * ощущение от боя.
 *
 * <p>Разбираем по одному полю, а не запись целиком. Пропущенное поле берёт
 * встроенное значение, испорченное — тоже, с записью в журнал. Так одна опечатка
 * не обнуляет весь файл и не оставляет измерение без мобов: настройка боя не
 * должна уметь ломать игру.
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
     * Характеристики одного вида.
     *
     * @param health           здоровье
     * @param damage           урон удара
     * @param armor            броня
     * @param knockback        сопротивление отбрасыванию, 0..1
     * @param speed            скорость шага; у летающих задаёт разгон и повороты
     * @param flyingSpeed      скорость полёта; у Призрака и Поглотителя это
     *                         блоки за тик, то есть умножай на 20 для блоков в
     *                         секунду
     * @param detection        за сколько блоков чует жертву — сквозь стены и
     *                         кругом, без слепых углов
     * @param memoryTicks      сколько тиков помнит потерянную из виду жертву
     * @param attackTicks      сколько тиков между ударами
     * @param experience       сколько опыта роняет
     */
    public record Stats(double health, double damage, double armor, double knockback,
                        double speed, double flyingSpeed, double detection,
                        int memoryTicks, int attackTicks, int experience) {
    }

    private static final String FILE = "mobs.json";
    private static final String DEFAULTS = "/data/gimpanum/entity/default_mobs.json";

    /**
     * Встроенные значения — они же образец файла.
     *
     * <p>Держим их здесь, а не только в json, по необходимости: клиент
     * выделенного сервера файла не видит вовсе, а моба всё равно создаёт, и
     * взять числа ему больше неоткуда.
     */
    private static final Map<String, Stats> BUILT_IN = Map.of(
            "dune_walker", new Stats(25.0, 12.0, 2.0, 0.0, 0.24, 0.0, 96.0, 12000, 50, 6),
            "comet_wraith", new Stats(12.0, 24.0, 0.0, 0.0, 0.42, 3.0, 60.0, 400, 24, 5),
            "space_devourer", new Stats(100.0, 40.0, 4.0, 0.5, 0.57, 2.0, 80.0, 3600, 40, 20),
            "plasma_bolt", new Stats(60.0, 32.0, 0.0, 0.0, 0.205, 0.97, 64.0, 400, 60, 8));

    private static Map<String, Stats> loaded = BUILT_IN;

    private MobStats() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve(FILE);
    }

    public static int count() {
        return loaded.size();
    }

    /** Числа для вида по его пути в реестре, например {@code dune_walker}. */
    public static Stats of(String name) {
        return loaded.getOrDefault(name, BUILT_IN.getOrDefault(name, BUILT_IN.get("dune_walker")));
    }

    public static void load() {
        Path file = path();
        Map<String, Stats> read = new LinkedHashMap<>(BUILT_IN);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                writeDefaults(file);
                Gimpanum.LOGGER.info("Создан пример характеристик мобов: {}", file);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject()) {
                    for (String name : BUILT_IN.keySet()) {
                        JsonElement section = root.getAsJsonObject().get(name);
                        if (section != null && section.isJsonObject()) {
                            read.put(name, parse(name, section.getAsJsonObject(), BUILT_IN.get(name)));
                        }
                    }
                }
            }
            loaded = Map.copyOf(read);
            Gimpanum.LOGGER.info("Характеристики мобов прочитаны: {}", file);
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Не прочитались характеристики мобов {} — беру встроенные", file, failure);
            loaded = BUILT_IN;
        }
    }

    private static Stats parse(String name, JsonObject json, Stats fallback) {
        return new Stats(
                number(name, json, "health", fallback.health()),
                number(name, json, "damage", fallback.damage()),
                number(name, json, "armor", fallback.armor()),
                number(name, json, "knockback_resistance", fallback.knockback()),
                number(name, json, "movement_speed", fallback.speed()),
                number(name, json, "flying_speed", fallback.flyingSpeed()),
                number(name, json, "detection_blocks", fallback.detection()),
                (int) number(name, json, "memory_ticks", fallback.memoryTicks()),
                (int) number(name, json, "attack_interval_ticks", fallback.attackTicks()),
                (int) number(name, json, "experience", fallback.experience()));
    }

    private static double number(String name, JsonObject json, String field, double fallback) {
        JsonElement value = json.get(field);
        if (value == null) {
            return fallback;
        }
        try {
            return value.getAsDouble();
        } catch (RuntimeException broken) {
            Gimpanum.LOGGER.warn("{}.{} — не число, беру {}", name, field, fallback);
            return fallback;
        }
    }

    private static void writeDefaults(Path file) throws IOException {
        try (InputStream source = MobStats.class.getResourceAsStream(DEFAULTS)) {
            if (source == null) {
                throw new IOException("образец " + DEFAULTS + " не найден в джарке");
            }
            Files.copy(source, file);
        }
    }

    /**
     * Назначить мобу его числа.
     *
     * <p>Не через {@code createAttributes}: реестр атрибутов собирается при
     * запуске игры, задолго до того, как появится сервер и его папка настроек.
     * Поэтому встроенные значения остаются основой, а настройка ложится поверх —
     * каждому мобу при появлении в мире.
     */
    public static void apply(LivingEntity entity) {
        String name = key(entity.getType());
        if (name == null) {
            return;
        }
        Stats stats = of(name);
        boolean full = entity.getHealth() >= entity.getMaxHealth();
        set(entity, Attributes.MAX_HEALTH, stats.health());
        set(entity, Attributes.ATTACK_DAMAGE, stats.damage());
        set(entity, Attributes.ARMOR, stats.armor());
        set(entity, Attributes.KNOCKBACK_RESISTANCE, stats.knockback());
        set(entity, Attributes.MOVEMENT_SPEED, stats.speed());
        set(entity, Attributes.FLYING_SPEED, stats.flyingSpeed());
        set(entity, Attributes.FOLLOW_RANGE, stats.detection());
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

    /** Путь вида в реестре, если это наш моб. */
    public static String key(EntityType<?> type) {
        if (type == GimpanumEntities.DUNE_WALKER.get()) {
            return "dune_walker";
        }
        if (type == GimpanumEntities.COMET_WRAITH.get()) {
            return "comet_wraith";
        }
        if (type == GimpanumEntities.SPACE_DEVOURER.get()) {
            return "space_devourer";
        }
        if (type == GimpanumEntities.PLASMA_BOLT.get()) {
            return "plasma_bolt";
        }
        return null;
    }
}
