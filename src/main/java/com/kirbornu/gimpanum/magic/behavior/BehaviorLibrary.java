package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.config.BehaviorSpec;
import com.kirbornu.gimpanum.magic.config.Json;
import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        register("water", Behavior.Kind.ACTION, Set.of("seconds"), p -> {
            Double seconds = p.number("seconds", -1.0, MAX_SECONDS);
            if (seconds != null && seconds != -1.0 && seconds <= 0) {
                p.errors().add(p.path("seconds"), "-1 — навсегда, иначе больше нуля");
                return null;
            }
            return seconds == null ? null : new Actions.Water(seconds);
        });
        register("light", Behavior.Kind.ACTION, Set.of("level", "seconds"), p -> {
            Integer level = p.integer("level", 0, 15);
            Double seconds = p.number("seconds", -1.0, MAX_SECONDS);
            if (seconds != null && seconds != -1.0 && seconds <= 0) {
                p.errors().add(p.path("seconds"), "-1 — навсегда, иначе больше нуля");
                return null;
            }
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
