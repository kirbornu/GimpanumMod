package com.kirbornu.gimpanum.magic.particle;

import com.kirbornu.gimpanum.magic.core.Role;

import java.util.EnumMap;
import java.util.Map;

/**
 * Частицы аркана по ролям.
 *
 * <p>Узор задаёт роль, а не аркан: корень вспыхивает на целях, префикс
 * обводит то, что выбрал, суффикс закручивает спираль вокруг целей,
 * интерфикс оставляет след за сущностью-заклинанием. Аркан выбирает только,
 * <i>какими</i> частицами рисовать. Так по картинке видно и что это за аркан, и
 * какую роль он сейчас играет.
 */
public final class ArcaneParticles {

    public static final ArcaneParticles NONE = new ArcaneParticles(Map.of());

    private final Map<Role, ParticleLayer> layers;

    public ArcaneParticles(Map<Role, ParticleLayer> layers) {
        this.layers = layers.isEmpty() ? Map.of() : new EnumMap<>(layers);
    }

    /** @return слой роли или {@code null}, если аркан в этой роли не искрит */
    public ParticleLayer layer(Role role) {
        return layers.get(role);
    }
}
