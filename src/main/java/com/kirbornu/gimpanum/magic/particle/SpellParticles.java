package com.kirbornu.gimpanum.magic.particle;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;
import java.util.Iterator;

/**
 * Узоры частиц и общий на сервер бюджет на тик.
 *
 * <p>Все частицы рисуются сервером через {@link ServerLevel#sendParticles}:
 * клиент о заклинаниях ничего не знает и знать не должен. Бюджет общий,
 * потому что частицы — чистая красота, и сотня одновременных заклинаний не
 * должна топить клиентов в пакетах. Когда бюджет кончился, частицы этого тика
 * просто не рисуются.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class SpellParticles {

    /** Точек на блок длины у обводок: реже — контур рвётся, чаще — каша. */
    private static final double OUTLINE_DENSITY = 3.0;

    /** Сколько блоков из выделения искрит при срабатывании корня, чтобы большая форма не съела бюджет. */
    private static final int MAX_BLOCK_BURSTS = 24;

    private static int spentThisTick;

    private SpellParticles() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        spentThisTick = 0;
    }

    /** Одна вспышка слоя в точке, если хватает бюджета. */
    public static void burst(ServerLevel level, ParticleLayer layer, Vec3 at) {
        if (layer == null) {
            return;
        }
        int budget = MagicConfig.get().settings().limits().particlesPerTick() - spentThisTick;
        int count = Math.min(Math.max(layer.count(), 1), budget);
        if (count <= 0) {
            return;
        }
        spentThisTick += count;
        level.sendParticles(layer.options(), at.x, at.y, at.z, count, layer.spread(), layer.spread(), layer.spread(),
                layer.speed());
    }

    /** Вспышки над блоками — не больше {@link #MAX_BLOCK_BURSTS}, равномерно по списку. */
    public static void blocks(ServerLevel level, ParticleLayer layer, Collection<BlockPos> blocks) {
        if (layer == null || blocks.isEmpty()) {
            return;
        }
        int step = Math.max(1, blocks.size() / MAX_BLOCK_BURSTS);
        Iterator<BlockPos> it = blocks.iterator();
        for (int i = 0; it.hasNext(); i++) {
            BlockPos pos = it.next();
            if (i % step == 0) {
                burst(level, layer, Vec3.atCenterOf(pos));
            }
        }
    }

    /** Отрезок — так префикс показывает, на кого пал взгляд. */
    public static void line(ServerLevel level, ParticleLayer layer, Vec3 from, Vec3 to) {
        if (layer == null) {
            return;
        }
        Vec3 delta = to.subtract(from);
        int points = Math.max(2, (int) Math.ceil(delta.length() * OUTLINE_DENSITY));
        for (int i = 0; i <= points; i++) {
            burst(level, layer, from.add(delta.scale((double) i / points)));
        }
    }

    /** Три окружности — сфера радиуса, которой префикс собрал цели. */
    public static void sphere(ServerLevel level, ParticleLayer layer, Vec3 center, double radius) {
        if (layer == null) {
            return;
        }
        int points = Math.max(8, (int) Math.ceil(Mth.TWO_PI * radius * OUTLINE_DENSITY));
        for (int i = 0; i < points; i++) {
            double a = Mth.TWO_PI * i / points;
            double c = Math.cos(a) * radius;
            double s = Math.sin(a) * radius;
            burst(level, layer, center.add(c, 0, s));
            burst(level, layer, center.add(c, s, 0));
            burst(level, layer, center.add(0, c, s));
        }
    }

    /** Контур конуса: образующие и окружность основания. */
    public static void cone(ServerLevel level, ParticleLayer layer, Vec3 apex, Vec3 direction, double halfAngle,
                            double length) {
        if (layer == null) {
            return;
        }
        Vec3 axis = direction.normalize();
        Vec3 side = Math.abs(axis.y) < 0.99 ? axis.cross(new Vec3(0, 1, 0)).normalize()
                : axis.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(axis).normalize();
        double radius = Math.tan(halfAngle) * length;
        Vec3 base = apex.add(axis.scale(length));
        int rim = Math.max(8, (int) Math.ceil(Mth.TWO_PI * radius * OUTLINE_DENSITY));
        for (int i = 0; i < rim; i++) {
            double a = Mth.TWO_PI * i / rim;
            burst(level, layer, base.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius)));
        }
        for (int k = 0; k < 4; k++) {
            double a = Mth.HALF_PI * k;
            line(level, layer, apex, base.add(side.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius)));
        }
    }

    /** Спираль вокруг цели — отличительный знак суффикса. */
    public static void spiral(ServerLevel level, ParticleLayer layer, Vec3 bottom, double radius, double height) {
        if (layer == null) {
            return;
        }
        int points = 32;
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double a = Mth.TWO_PI * 2 * t;
            burst(level, layer, bottom.add(Math.cos(a) * radius, t * height, Math.sin(a) * radius));
        }
    }

    /** Кольцо на земле вокруг точки — так видно, что сработал префикс без формы. */
    public static void ring(ServerLevel level, ParticleLayer layer, Vec3 center, double radius) {
        if (layer == null) {
            return;
        }
        int points = Math.max(8, (int) Math.ceil(Mth.TWO_PI * radius * OUTLINE_DENSITY));
        for (int i = 0; i < points; i++) {
            double a = Mth.TWO_PI * i / points;
            burst(level, layer, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
    }
}
