package com.kirbornu.gimpanum.emission;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Какие бывают выбросы.
 *
 * <p>Имя — оно же раздел в {@code emissions.json}, ключ перевода и аргумент
 * команды. Звук — то, с чем выброс обрушивается: по нему событие узнают ещё
 * до того, как прочтут название.
 */
public enum EmissionKind {
    FRENZIED_RAID("frenzied_raid", FrenziedRaid::new, () -> SoundEvents.RAID_HORN.value()),
    SLEEPING_GOD("sleeping_god", SleepingGod::new, () -> SoundEvents.WITHER_SPAWN),
    WRATH("wrath", Wrath::new, () -> SoundEvents.BLAZE_SHOOT),
    DESPONDENCY("despondency", Affliction::despondency, () -> SoundEvents.ELDER_GUARDIAN_CURSE),
    ENVY("envy", Affliction::envy, () -> SoundEvents.WITCH_CELEBRATE),
    BAD_MEMORIES("bad_memories", BadMemories::new, () -> SoundEvents.ENDERMAN_STARE),
    NOSTALGIA("nostalgia", Nostalgia::new, () -> SoundEvents.NOTE_BLOCK_CHIME.value());

    private final String id;
    private final Supplier<Emission> factory;
    private final Supplier<SoundEvent> sound;

    EmissionKind(String id, Supplier<Emission> factory, Supplier<SoundEvent> sound) {
        this.id = id;
        this.factory = factory;
        this.sound = sound;
    }

    public String id() {
        return id;
    }

    Emission create() {
        return factory.get();
    }

    SoundEvent sound() {
        return sound.get();
    }

    public Component title() {
        return Component.translatable("gimpanum.emission." + id);
    }

    Component hint() {
        return Component.translatable("gimpanum.emission." + id + ".hint");
    }

    double weight() {
        return EmissionConfig.of(id).number("weight");
    }

    public static Optional<EmissionKind> byId(String id) {
        for (EmissionKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
