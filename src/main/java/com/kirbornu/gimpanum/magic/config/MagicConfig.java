package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.behavior.BehaviorLibrary;
import com.kirbornu.gimpanum.magic.core.FieldSet;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.ArcaneParticles;
import com.kirbornu.gimpanum.magic.particle.ParticleLayer;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Настройки магии — {@code config/gimpanum/magic/}: {@code arcanes.json},
 * {@code fields.json}, {@code magic.json}.
 *
 * <p>В отличие от прочих настроек мода, эти файлы не сливаются с образцом по
 * полю, а читаются целиком и строго. Арканы — это списки и связи между
 * файлами, и «взять встроенное значение там, где опечатка» дало бы аркан,
 * который ведёт себя наполовину по-старому. Поэтому правило одно: три файла
 * читаются вместе, и если хоть в одном ошибка, не применяется ничего — остаются
 * прежние настройки, а ошибки показываются поимённо, с путём до места.
 *
 * <p>Формат описан в {@code docs/magic-config.md}: те же {@code arcanes.json}
 * и {@code fields.json} читает браузерная песочница автора.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class MagicConfig {

    private static final String RESOURCE_DIR = "/data/gimpanum/magic/";

    /** Действующие настройки. */
    public record Loaded(MagicSettings settings, Map<String, Arcane> arcanes, FieldsConfig fields,
                         Item upgradeItem) {

        public Arcane arcane(String id) {
            return arcanes.get(id);
        }

        /** Набор полей для предмета во второй руке. */
        public FieldSet fieldsFor(ItemStack catalyst) {
            return fields.forCatalyst(catalyst.isEmpty() ? null
                    : BuiltInRegistries.ITEM.getKey(catalyst.getItem()).toString());
        }
    }

    private static Loaded current;

    private MagicConfig() {
    }

    /**
     * Действующие настройки.
     *
     * @throws IllegalStateException если сервер ещё не прочитал их ни разу
     */
    public static Loaded get() {
        if (current == null) {
            throw new IllegalStateException("настройки магии ещё не прочитаны");
        }
        return current;
    }

    public static boolean ready() {
        return current != null;
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve("magic");
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        List<String> errors = load(event.getServer());
        if (!errors.isEmpty() && current == null) {
            // Первый запуск с испорченными файлами: играть без магии хуже, чем
            // с образцом, поэтому берём образец из джарки и громко пишем в журнал.
            Gimpanum.LOGGER.error("Настройки магии с ошибками — беру встроенный образец");
            ConfigErrors builtIn = new ConfigErrors();
            current = compile(readBuiltIn(builtIn), event.getServer().registryAccess(), builtIn);
            if (current == null) {
                throw new IllegalStateException("встроенный образец магии не читается: " + builtIn.messages());
            }
        }
    }

    /**
     * Перечитать три файла. Недостающие сначала копируются из образца.
     *
     * @return ошибки; пусто — настройки применены
     */
    public static List<String> load(MinecraftServer server) {
        ConfigErrors errors = new ConfigErrors();
        Map<String, JsonElement> files = new LinkedHashMap<>();
        for (String name : List.of(MagicConfigParser.ARCANES, MagicConfigParser.FIELDS, MagicConfigParser.MAGIC)) {
            files.put(name, readFile(name, errors));
        }
        Loaded loaded = errors.isEmpty() ? compile(files, server.registryAccess(), errors) : null;
        if (loaded == null) {
            for (String message : errors.messages()) {
                Gimpanum.LOGGER.error("Магия: {}", message);
            }
            return errors.messages();
        }
        current = loaded;
        Gimpanum.LOGGER.info("Настройки магии прочитаны из {}: арканов {}, наборов полей {}", dir(),
                loaded.arcanes().size(), loaded.fields().sets().size());
        return List.of();
    }

    private static Loaded compile(Map<String, JsonElement> files, HolderLookup.Provider registries,
                                  ConfigErrors errors) {
        // Разбор частичный: поведения и частицы уцелевших арканов проверяются
        // в том же проходе, чтобы все ошибки были видны за одну перезагрузку.
        MagicConfigParser.Parsed parsed = MagicConfigParser.parsePartially(files.get(MagicConfigParser.ARCANES),
                files.get(MagicConfigParser.FIELDS), files.get(MagicConfigParser.MAGIC), errors);
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);

        Map<String, Arcane> arcanes = new LinkedHashMap<>();
        for (ArcaneDef def : parsed.arcanes() == null ? List.<ArcaneDef>of() : parsed.arcanes().values()) {
            Map<Role, List<Behavior>> behaviors = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                List<Behavior> list = new ArrayList<>();
                for (BehaviorSpec spec : def.behaviors(role)) {
                    Behavior behavior = BehaviorLibrary.compile(spec, role);
                    if (behavior != null) {
                        list.add(behavior);
                    }
                }
                behaviors.put(role, List.copyOf(list));
            }
            arcanes.put(def.id(), new Arcane(def, behaviors, particles(def.particles(), ops)));
        }

        // Id предметов приводятся к полному виду: «sculk» в файле и
        // «minecraft:sculk» у предмета в руке — один и тот же катализатор.
        Map<String, String> catalysts = new LinkedHashMap<>();
        Map<String, String> declared = parsed.fields() == null ? Map.of() : parsed.fields().catalysts();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            Item item = item(entry.getKey(), MagicConfigParser.FIELDS + ".sets." + entry.getValue() + ".catalysts",
                    errors);
            if (item != null) {
                catalysts.put(BuiltInRegistries.ITEM.getKey(item).toString(), entry.getValue());
            }
        }
        Item upgrade = parsed.settings() == null ? null
                : item(parsed.settings().tabletUpgradeItem(), MagicConfigParser.MAGIC + ".tabletUpgradeItem", errors);
        if (!errors.isEmpty() || parsed.settings() == null || parsed.arcanes() == null || parsed.fields() == null) {
            return null;
        }
        return new Loaded(parsed.settings(), arcanes, new FieldsConfig(parsed.fields().sets(), catalysts), upgrade);
    }

    private static Item item(String id, String path, ConfigErrors errors) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            errors.add(path, "нет такого предмета: " + id);
            return null;
        }
        return BuiltInRegistries.ITEM.get(location);
    }

    /**
     * Частицы аркана: по слою на роль.
     *
     * <p>Сама частица описывается так же, как в ванильной команде
     * {@code /particle} в виде JSON, и разбирается ванильным же кодеком —
     * так доступны все частицы игры с их параметрами, а формат не наш и не
     * поменяется вместе с модом.
     */
    private static ArcaneParticles particles(Json json, DynamicOps<JsonElement> ops) {
        if (json == null) {
            return ArcaneParticles.NONE;
        }
        Map<Role, ParticleLayer> layers = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            if (!json.has(role.key())) {
                continue;
            }
            Json layer = json.object(role.key());
            if (layer == null) {
                continue;
            }
            layer.rejectUnknown(Set.of("particle", "count", "spread", "speed"));
            JsonElement particle = layer.raw().get("particle");
            ParticleOptions options = null;
            if (particle == null) {
                layer.errors().add(layer.path("particle"), "нет описания частицы");
            } else {
                DataResult<ParticleOptions> result = ParticleTypes.CODEC.parse(ops, particle);
                options = result.resultOrPartial().orElse(null);
                result.error().ifPresent(error ->
                        layer.errors().add(layer.path("particle"), "частица не разобралась: " + error.message()));
            }
            int count = layer.has("count") ? orDefault(layer.integer("count", 1, 1000), 1) : 1;
            double spread = layer.has("spread") ? orDefault(layer.number("spread", 0.0, 16.0), 0.0) : 0.0;
            double speed = layer.has("speed") ? orDefault(layer.number("speed", 0.0, 10.0), 0.0) : 0.0;
            if (options != null) {
                layers.put(role, new ParticleLayer(options, count, spread, speed));
            }
        }
        return new ArcaneParticles(layers);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static JsonElement readFile(String name, ConfigErrors errors) {
        Path path = dir().resolve(name);
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                try (InputStream source = open(name)) {
                    Files.copy(source, path);
                }
                Gimpanum.LOGGER.info("Создан пример настроек магии: {}", path);
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (JsonParseException failure) {
            errors.add(name, "это не JSON: " + rootMessage(failure));
        } catch (IOException failure) {
            errors.add(name, "не читается: " + failure.getMessage());
        }
        return new JsonObject();
    }

    private static Map<String, JsonElement> readBuiltIn(ConfigErrors errors) {
        Map<String, JsonElement> files = new LinkedHashMap<>();
        for (String name : List.of(MagicConfigParser.ARCANES, MagicConfigParser.FIELDS, MagicConfigParser.MAGIC)) {
            try (Reader reader = new InputStreamReader(open(name), StandardCharsets.UTF_8)) {
                files.put(name, JsonParser.parseReader(reader));
            } catch (IOException | JsonParseException failure) {
                errors.add(name, "встроенный образец не читается: " + failure.getMessage());
                files.put(name, new JsonObject());
            }
        }
        return files;
    }

    private static InputStream open(String name) throws IOException {
        InputStream source = MagicConfig.class.getResourceAsStream(RESOURCE_DIR + name);
        if (source == null) {
            throw new IOException("образец " + name + " не найден в джарке");
        }
        return source;
    }

    /** Gson прячет строку и столбец в причине — их и показываем, без стека. */
    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
