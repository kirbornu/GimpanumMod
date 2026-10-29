package com.kirbornu.gimpanum.magic.sound;

import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

/**
 * Звуки аркана по ролям.
 *
 * <p>Как и с частицами, момент задаёт роль, а аркан — каким звуком: корень
 * звучит при каждом срабатывании, префикс и суффикс — один раз, когда
 * отработали, интерфикс — в начале. Звук слышат все рядом, как любой
 * ванильный звук в мире; категория — «Игроки», её громкость игрок крутит в
 * настройках.
 */
public final class ArcaneSounds {

    public static final ArcaneSounds NONE = new ArcaneSounds(Map.of());

    /**
     * Один звук.
     *
     * @param volume громкость; больше 1 — слышно дальше (16 блоков на единицу)
     * @param pitch  высота тона, от 0.5 до 2
     */
    public record Sound(Holder<SoundEvent> event, float volume, float pitch) {
    }

    private final Map<Role, Sound> sounds;

    public ArcaneSounds(Map<Role, Sound> sounds) {
        this.sounds = sounds.isEmpty() ? Map.of() : new EnumMap<>(sounds);
    }

    /** Прозвучать за роль в точке, если у аркана в этой роли есть звук. */
    public void play(ServerLevel level, Role role, Vec3 at) {
        Sound sound = sounds.get(role);
        if (sound != null) {
            level.playSound(null, at.x, at.y, at.z, sound.event(), SoundSource.PLAYERS, sound.volume(), sound.pitch());
        }
    }
}
