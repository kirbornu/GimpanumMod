package com.kirbornu.gimpanum.magic.particle;

import net.minecraft.core.particles.ParticleOptions;

/**
 * Один слой частиц: что пускать и сколько.
 *
 * @param count  частиц в одной точке узора
 * @param spread разброс вокруг точки, в блоках
 * @param speed  скорость частиц — у большинства ванильных это разлёт
 */
public record ParticleLayer(ParticleOptions options, int count, double spread, double speed) {
}
