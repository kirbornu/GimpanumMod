package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.config.JsonConfig;
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
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
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
 * <p>Встроенные значения — образец из джарки, файл сливается с ним по полю:
 * см. {@link JsonConfig}. Раньше число повторялось трижды — в атрибутах
 * сущности, в таблице здесь и в образце, — и правка в одном из них молча
 * расходилась с двумя другими.
 *
 * <p><b>Что можно менять на живом сервере, а что нет.</b> Числа-атрибуты —
 * здоровье, урон, броня, скорость, чутьё — применяются каждому мобу при
 * появлении в мире и переназначаются всем уже стоящим при перечитывании. А вот
 * память и темп удара уходят внутрь целей поведения в момент создания моба, и
 * заменить их у живого нельзя: они подействуют на тех, кто появится после
 * перечитывания.
 */
public final class MobStats {

    private static final JsonConfig CONFIG = new JsonConfig("mobs.json", "/data/gimpanum/entity/default_mobs.json");

    /** Какое поле какой атрибут задаёт. Поля нет у моба — атрибут не трогаем. */
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = Map.of(
            "health", Attributes.MAX_HEALTH,
            "damage", Attributes.ATTACK_DAMAGE,
            "armor", Attributes.ARMOR,
            "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE,
            "movement_speed", Attributes.MOVEMENT_SPEED,
            "flying_speed", Attributes.FLYING_SPEED,
            "detection_blocks", Attributes.FOLLOW_RANGE);

    private MobStats() {
    }

    public static Path path() {
        return CONFIG.path();
    }

    /** Сколько разделов прочитано — мобов и общих настроек. */
    public static int count() {
        return sections().size();
    }

    /** Имена всех разделов — мобов и общих настроек. */
    public static List<String> sections() {
        return CONFIG.sections();
    }

    /** Числа из файла для раздела, например {@code dune_walker} или {@code spawner}. */
    public static JsonConfig.Section of(String name) {
        return CONFIG.section(name);
    }

    /**
     * Числа из образца, без файла настроек.
     *
     * <p>Для {@code createAttributes}: реестр атрибутов собирается при запуске
     * игры, задолго до того, как появится сервер и его папка настроек.
     */
    public static JsonConfig.Section builtIn(String name) {
        return CONFIG.builtIn(name);
    }

    /** Атрибуты из образца — основа, на которую потом ляжет файл. */
    public static AttributeSupplier.Builder attributes(AttributeSupplier.Builder builder, String name) {
        JsonConfig.Section stats = builtIn(name);
        ATTRIBUTES.forEach((field, attribute) -> {
            if (stats.has(field)) {
                builder.add(attribute, stats.number(field));
            }
        });
        return builder;
    }

    public static void load() {
        CONFIG.load();
        MobLoot.check();
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
        JsonConfig.Section stats = of(name);
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
        return Gimpanum.MOD_ID.equals(id.getNamespace()) && CONFIG.knows(id.getPath()) ? id.getPath() : null;
    }
}
