package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.config.BehaviorSpec;
import com.kirbornu.gimpanum.magic.config.Json;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.spell.SpellContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Библиотека поведений — все имена, которые можно писать в {@code arcanes.json}.
 *
 * <p>Имена и параметры — часть формата файла, поэтому они стабильны:
 * переименовать поведение значит сломать чужие настройки. Описание каждого —
 * в {@code docs/magic-config.md}.
 *
 * <p>Пределы параметров здесь — не баланс, а защита сервера: конус длиной в
 * тысячу блоков перебрал бы миллиард позиций. Баланс задаётся в файле.
 */
public final class BehaviorLibrary {

    /** Самая большая форма и самый дальний поиск: дальше заклинание уже перебирает лишнее. */
    private static final double MAX_REACH = 32.0;
    private static final double MAX_SECONDS = 3600.0;
    private static final double MAX_AMOUNT = 1000.0;

    private static final Map<String, BehaviorType> TYPES = new LinkedHashMap<>();

    static {
        // --- изменения контекста ---
        register("extend_duration", Behavior.Kind.MODIFIER, Set.of("factor"),
                p -> build(p.number("factor", 0.0, 100.0), Modifiers.ExtendDuration::new));
        register("periodic_if_instant", Behavior.Kind.MODIFIER, Set.of("interval", "duration"), p -> {
            Double interval = p.number("interval", 0.05, MAX_SECONDS);
            Double duration = p.number("duration", 0.05, MAX_SECONDS);
            return interval == null || duration == null ? null : new Modifiers.PeriodicIfInstant(interval, duration);
        });
        register("amplify", Behavior.Kind.MODIFIER, Set.of("percent"),
                p -> build(p.number("percent", -100.0, 10_000.0), Modifiers.Amplify::new));
        register("target_look", Behavior.Kind.MODIFIER, Set.of("range"),
                p -> build(p.number("range", 0.0, MAX_REACH), Modifiers.TargetLook::new));
        register("target_radius", Behavior.Kind.MODIFIER, Set.of("radius", "includeCaster"), p -> {
            Double radius = p.number("radius", 0.0, MAX_REACH);
            Boolean caster = p.bool("includeCaster");
            return radius == null || caster == null ? null : new Modifiers.TargetRadius(radius, caster);
        });
        register("shape_cone", Behavior.Kind.MODIFIER, Set.of("angle", "length"), p -> {
            Double angle = p.number("angle", 0.0, 180.0);
            Double length = p.number("length", 0.0, MAX_REACH);
            return angle == null || length == null ? null : new Modifiers.ShapeCone(angle, length);
        });
        register("shape_sphere", Behavior.Kind.MODIFIER, Set.of("radius"),
                p -> build(p.number("radius", 0.0, MAX_REACH), Modifiers.ShapeSphere::new));

        register("target_caster", Behavior.Kind.MODIFIER, Set.of(), p -> new Modifiers.TargetCaster());
        register("target_nearest", Behavior.Kind.MODIFIER, Set.of("radius", "excludeCaster"), p -> {
            Double radius = p.number("radius", 0.0, MAX_REACH);
            Boolean exclude = p.bool("excludeCaster");
            return radius == null || exclude == null ? null : new Modifiers.TargetNearest(radius, exclude);
        });
        register("target_filter", Behavior.Kind.MODIFIER, Set.of("filter"),
                p -> build(targetFilter(p, "filter"), Modifiers.TargetFilter::new));
        register("target_limit", Behavior.Kind.MODIFIER, Set.of("count"),
                p -> build(p.integer("count", 0, 1000), Modifiers.TargetLimit::new));
        register("shape_line", Behavior.Kind.MODIFIER, Set.of("length", "width"), p -> {
            Double length = p.number("length", 0.0, MAX_REACH);
            Double width = p.number("width", 0.0, MAX_REACH);
            return length == null || width == null ? null : new Modifiers.ShapeLine(length, width);
        });
        register("shape_ring", Behavior.Kind.MODIFIER, Set.of("radius", "thickness"), p -> {
            Double radius = p.number("radius", 0.0, MAX_REACH);
            Double thickness = p.number("thickness", 0.0, MAX_REACH);
            return radius == null || thickness == null ? null : new Modifiers.ShapeRing(radius, thickness);
        });
        register("shape_box", Behavior.Kind.MODIFIER, Set.of("x", "y", "z"), p -> {
            Double x = p.number("x", 0.0, MAX_REACH);
            Double y = p.number("y", 0.0, MAX_REACH);
            Double z = p.number("z", 0.0, MAX_REACH);
            return x == null || y == null || z == null ? null : new Modifiers.ShapeBox(x, y, z);
        });
        register("shape_ground", Behavior.Kind.MODIFIER, Set.of("maxDepth"),
                p -> build(p.integer("maxDepth", 0, 64), Modifiers.ShapeGround::new));
        register("repeat", Behavior.Kind.MODIFIER, Set.of("times"),
                p -> build(p.integer("times", 1, SpellContext.MAX_REPEAT), Modifiers.Repeat::new));
        register("delay", Behavior.Kind.MODIFIER, Set.of("seconds"),
                p -> build(p.number("seconds", 0.0, MAX_SECONDS), Modifiers.Delay::new));

        // --- действия ---
        register("damage", Behavior.Kind.ACTION, Set.of("amount"),
                p -> build(p.number("amount", 0.0, MAX_AMOUNT), Actions.Damage::new));
        register("ignite", Behavior.Kind.ACTION, Set.of("seconds"),
                p -> build(p.number("seconds", 0.0, MAX_SECONDS), Actions.Ignite::new));
        register("heal", Behavior.Kind.ACTION, Set.of("amount"),
                p -> build(p.number("amount", 0.0, MAX_AMOUNT), Actions.Heal::new));
        register("effect", Behavior.Kind.ACTION, Set.of("effect", "seconds", "amplifier"), p -> {
            Holder<MobEffect> effect = effect(p);
            Double seconds = p.number("seconds", 0.0, MAX_SECONDS);
            int amplifier = p.has("amplifier") ? orZero(p.integer("amplifier", 0, 255)) : 0;
            return effect == null || seconds == null ? null : new Actions.Effect(effect, seconds, amplifier);
        });
        register("cleanse", Behavior.Kind.ACTION, Set.of(), p -> new Actions.Cleanse());
        register("echo_root", Behavior.Kind.ACTION, Set.of("interval", "duration"), p -> {
            Double interval = p.number("interval", 0.05, MAX_SECONDS);
            Double duration = p.number("duration", 0.05, MAX_SECONDS);
            return interval == null || duration == null ? null : new Actions.EchoRoot(interval, duration);
        });
        register("water", Behavior.Kind.ACTION, Set.of("seconds"),
                p -> build(foreverOrPositive(p, "seconds"), Actions.Water::new));
        register("light", Behavior.Kind.ACTION, Set.of("level", "seconds"), p -> {
            Integer level = p.integer("level", 0, 15);
            Double seconds = foreverOrPositive(p, "seconds");
            return level == null || seconds == null ? null : new Actions.Light(level, seconds);
        });

        register("knockback", Behavior.Kind.ACTION, Set.of("strength"),
                p -> build(p.number("strength", 0.0, 10.0), Actions.Knockback::new));
        register("pull", Behavior.Kind.ACTION, Set.of("strength"),
                p -> build(p.number("strength", 0.0, 10.0), Actions.Pull::new));
        register("launch", Behavior.Kind.ACTION, Set.of("strength"),
                p -> build(p.number("strength", 0.0, 10.0), Actions.Launch::new));
        register("freeze", Behavior.Kind.ACTION, Set.of("seconds"),
                p -> build(p.number("seconds", 0.0, MAX_SECONDS), Actions.Freeze::new));
        register("feed", Behavior.Kind.ACTION, Set.of("food", "saturation"), p -> {
            Integer food = p.integer("food", 0, 20);
            Double saturation = p.number("saturation", 0.0, 20.0);
            return food == null || saturation == null ? null : new Actions.Feed(food, saturation);
        });
        register("absorb", Behavior.Kind.ACTION, Set.of("amount", "seconds"), p -> {
            Double amount = p.number("amount", 0.0, MAX_AMOUNT);
            Double seconds = p.number("seconds", 0.0, MAX_SECONDS);
            return amount == null || seconds == null ? null : new Actions.Absorb(amount, seconds);
        });
        register("extinguish", Behavior.Kind.ACTION, Set.of(), p -> new Actions.Extinguish());

        register("set_fire_blocks", Behavior.Kind.ACTION, Set.of("seconds"),
                p -> build(foreverOrPositive(p, "seconds"), Actions.SetFireBlocks::new));
        register("explode", Behavior.Kind.ACTION, Set.of("power", "breakBlocks"), p -> {
            Double power = p.number("power", 0.0, Actions.MAX_EXPLOSION);
            Boolean breakBlocks = p.bool("breakBlocks");
            return power == null || breakBlocks == null ? null : new Actions.Explode(power, breakBlocks);
        });
        register("lightning", Behavior.Kind.ACTION, Set.of(), p -> new Actions.Lightning());
        register("teleport_to_origin", Behavior.Kind.ACTION, Set.of(), p -> new Actions.TeleportToOrigin());
        register("swap_caster", Behavior.Kind.ACTION, Set.of(), p -> new Actions.SwapCaster());
        register("remove_effect", Behavior.Kind.ACTION, Set.of("effect"),
                p -> build(effect(p), Actions.RemoveEffect::new));
        register("steal_effects", Behavior.Kind.ACTION, Set.of(), p -> new Actions.StealEffects());
        register("drain", Behavior.Kind.ACTION, Set.of("amount"),
                p -> build(p.number("amount", 0.0, MAX_AMOUNT), Actions.Drain::new));

        register("place_block", Behavior.Kind.ACTION, Set.of("block", "seconds", "onlyAir"), p -> {
            BlockState block = blockState(p, "block");
            Double seconds = foreverOrPositive(p, "seconds");
            Boolean onlyAir = p.bool("onlyAir");
            return block == null || seconds == null || onlyAir == null ? null
                    : new Actions.PlaceBlock(block, seconds, onlyAir);
        });
        register("freeze_water", Behavior.Kind.ACTION, Set.of("seconds"),
                p -> build(foreverOrPositive(p, "seconds"), Actions.FreezeWater::new));
        register("grow", Behavior.Kind.ACTION, Set.of(), p -> new Actions.Grow());
        register("break_blocks", Behavior.Kind.ACTION, Set.of("maxHardness", "drop"), p -> {
            Double hardness = p.number("maxHardness", 0.0, 1000.0);
            Boolean drop = p.bool("drop");
            return hardness == null || drop == null ? null : new Actions.BreakBlocks(hardness, drop);
        });
        register("summon", Behavior.Kind.ACTION, Set.of("entity", "seconds"), p -> {
            EntityType<?> type = entityType(p, "entity");
            if (type != null && (!type.canSummon() || type == EntityType.PLAYER)) {
                p.errors().add(p.path("entity"), "это существо нельзя призвать");
                type = null;
            }
            Double seconds = foreverOrPositive(p, "seconds");
            return type == null || seconds == null ? null : new Actions.Summon(type, seconds);
        });
        register("spawn_projectile", Behavior.Kind.ACTION, Set.of("entity", "speed"), p -> {
            EntityType<?> type = entityType(p, "entity");
            Double speed = p.number("speed", 0.0, 10.0);
            return type == null || speed == null ? null : new Actions.SpawnProjectile(type, speed);
        });

        // --- процессы интерфикса ---
        register("wait", Behavior.Kind.PROCESS, Set.of("seconds"),
                p -> build(p.number("seconds", 0.0, MAX_SECONDS), Processes.Wait::new));
        register("descend", Behavior.Kind.PROCESS, Set.of("maxDistance", "speed"), p -> {
            Double distance = p.number("maxDistance", 0.0, 256.0);
            Double speed = p.number("speed", 0.01, 16.0);
            return distance == null || speed == null ? null : new Processes.Descend(distance, speed);
        });
        register("home", Behavior.Kind.PROCESS, Set.of("seconds", "speed", "excludeCaster"), p -> {
            Double seconds = p.number("seconds", 0.0, MAX_SECONDS);
            Double speed = p.number("speed", 0.0, 16.0);
            Boolean exclude = p.bool("excludeCaster");
            return seconds == null || speed == null || exclude == null ? null
                    : new Processes.Home(seconds, speed, exclude);
        });
        register("emit_light", Behavior.Kind.PROCESS, Set.of("level"), p -> {
            Integer level = p.integer("level", 0, 15);
            return level == null ? null : new Processes.EmitLight(level);
        });
    }

    private BehaviorLibrary() {
    }

    private static void register(String name, Behavior.Kind kind, Set<String> params, Function<Json, Behavior> factory) {
        TYPES.put(name, new BehaviorType(name, kind, params, factory));
    }

    private static <T> Behavior build(T value, Function<T, Behavior> factory) {
        return value == null ? null : factory.apply(value);
    }

    /** Срок, где −1 значит «навсегда», а иначе он больше нуля. */
    private static Double foreverOrPositive(Json p, String field) {
        Double seconds = p.number(field, -1.0, MAX_SECONDS);
        if (seconds != null && seconds != -1.0 && seconds <= 0) {
            p.errors().add(p.path(field), "-1 — навсегда, иначе больше нуля");
            return null;
        }
        return seconds;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static Holder<MobEffect> effect(Json p) {
        String id = p.string("effect");
        if (id == null) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        Optional<Holder.Reference<MobEffect>> effect = location == null ? Optional.empty()
                : BuiltInRegistries.MOB_EFFECT.getHolder(location);
        if (effect.isEmpty()) {
            p.errors().add(p.path("effect"), "нет такого эффекта: " + id);
            return null;
        }
        return effect.get();
    }

    /** Состояние блока в записи команды {@code /setblock}: {@code minecraft:oak_log[axis=x]}. */
    private static BlockState blockState(Json p, String field) {
        String id = p.string(field);
        if (id == null) {
            return null;
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
        } catch (CommandSyntaxException failure) {
            p.errors().add(p.path(field), "не разобрать блок «" + id + "»: " + failure.getRawMessage().getString());
            return null;
        }
    }

    /**
     * Фильтр целей: {@code players}, {@code monsters}, {@code animals},
     * {@code not_caster}, {@code type:<id существа>}, {@code tag:<тег типов
     * существ>}; {@code !} в начале обращает любой из них.
     */
    private static BiPredicate<LivingEntity, LivingEntity> targetFilter(Json p, String field) {
        String text = p.string(field);
        if (text == null) {
            return null;
        }
        boolean negate = text.startsWith("!");
        String body = negate ? text.substring(1) : text;
        BiPredicate<LivingEntity, LivingEntity> filter = switch (body) {
            case "players" -> (e, caster) -> e instanceof Player;
            case "monsters" -> (e, caster) -> e instanceof Enemy;
            case "animals" -> (e, caster) -> e instanceof Animal;
            case "not_caster" -> (e, caster) -> e != caster;
            default -> null;
        };
        if (filter == null && body.startsWith("type:")) {
            EntityType<?> type = entityType(p, field, body.substring("type:".length()));
            if (type == null) {
                return null;
            }
            filter = (e, caster) -> e.getType() == type;
        } else if (filter == null && body.startsWith("tag:")) {
            ResourceLocation location = ResourceLocation.tryParse(body.substring("tag:".length()));
            if (location == null) {
                p.errors().add(p.path(field), "не разобрать тег «" + body.substring(4) + "»");
                return null;
            }
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, location);
            filter = (e, caster) -> e.getType().is(tag);
        }
        if (filter == null) {
            p.errors().add(p.path(field), "нет такого фильтра «" + text + "»; есть: players, monsters, animals, "
                    + "not_caster, type:<id>, tag:<тег>, и любой с ! в начале");
            return null;
        }
        return negate ? filter.negate() : filter;
    }

    private static EntityType<?> entityType(Json p, String field) {
        String id = p.string(field);
        return id == null ? null : entityType(p, field, id);
    }

    /** Тип существа по id; ошибка записывается на поле {@code field}. */
    private static EntityType<?> entityType(Json p, String field, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Optional<EntityType<?>> type = location == null ? Optional.empty()
                : BuiltInRegistries.ENTITY_TYPE.getOptional(location);
        if (type.isEmpty()) {
            p.errors().add(p.path(field), "нет такого существа: " + id);
            return null;
        }
        return type.get();
    }

    public static Collection<BehaviorType> types() {
        return TYPES.values();
    }

    /**
     * Поведение из записи в файле.
     *
     * @return поведение или {@code null}; ошибки уже записаны
     */
    public static Behavior compile(BehaviorSpec spec, Role role) {
        Json params = spec.params();
        BehaviorType type = TYPES.get(spec.type());
        if (type == null) {
            params.errors().add(params.path("type"), "нет такого поведения «" + spec.type() + "»; есть: "
                    + String.join(", ", TYPES.keySet()));
            return null;
        }
        boolean interfix = role == Role.INTERFIX;
        if (interfix != (type.kind() == Behavior.Kind.PROCESS)) {
            params.errors().add(params.path("type"), interfix
                    ? "«" + type.name() + "» не процесс — в интерфиксе только " + String.join(", ",
                    TYPES.values().stream().filter(t -> t.kind() == Behavior.Kind.PROCESS).map(BehaviorType::name)
                            .toList())
                    : "«" + type.name() + "» — процесс, он бывает только в интерфиксе");
            return null;
        }
        Set<String> known = new HashSet<>(type.params());
        known.add("type");
        params.rejectUnknown(known);
        return type.factory().apply(params);
    }
}
