package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Голоса обитателей Гимпанума.
 *
 * <p>Звуковые файлы лежат в самом моде, а не берутся из ванильных событий, —
 * иначе их нельзя было бы заменить, не трогая ресурсы игры. Сейчас это копии
 * ванильных, но заменить любую можно, положив свой {@code .ogg} на то же
 * место.
 */
public final class GimpanumSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Gimpanum.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOURER_AMBIENT = event("entity.space_devourer.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOURER_HURT = event("entity.space_devourer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOURER_DEATH = event("entity.space_devourer.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOURER_STEP = event("entity.space_devourer.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEVOURER_ROAR = event("entity.space_devourer.roar");

    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_AMBIENT = event("entity.dune_walker.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_HURT = event("entity.dune_walker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_DEATH = event("entity.dune_walker.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALKER_STEP = event("entity.dune_walker.step");

    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_SCREAM = event("entity.comet_wraith.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_HURT = event("entity.comet_wraith.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRAITH_DEATH = event("entity.comet_wraith.death");

    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_AMBIENT = event("entity.plasma_bolt.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_HURT = event("entity.plasma_bolt.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_DEATH = event("entity.plasma_bolt.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_SHOOT = event("entity.plasma_bolt.shoot");

    private GimpanumSounds() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Gimpanum.id(name)));
    }
}
