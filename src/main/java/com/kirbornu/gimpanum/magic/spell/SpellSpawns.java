package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Что заклинания порождают в мире: призванные существа и снаряды.
 *
 * <p>Отметка — в постоянных данных сущности, поэтому она переживает
 * сохранение мира. По ней:
 * <ul>
 *     <li>призванное существо со сроком исчезает, когда срок вышел, — в том
 *     числе после перезапуска сервера (время игровое);</li>
 *     <li>призванное существо, убитое до срока, ничего не роняет и не даёт
 *     опыта: иначе заклинание стало бы фермой — призвал железного голема,
 *     убил, забрал железо;</li>
 *     <li>взрыв снаряда заклинания не ломает блоки (см. {@link SpellExplosions}):
 *     к моменту взрыва заклинания уже нет, и проверить, можно ли кастеру
 *     ломать там, нечем.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class SpellSpawns {

    private static final String KEY = "gimpanum_spell";
    private static final String KEY_SUMMONED = "Summoned";
    private static final String KEY_DISPEL_AT = "DispelAt";

    /** Раз в секунду: точнее для исчезновения не нужно. */
    private static final int CHECK_INTERVAL = 20;

    /**
     * Загруженные существа со сроком. Слабые ссылки: выгруженное из мира
     * существо уходит отсюда само, а при загрузке снова попадает через
     * {@link EntityJoinLevelEvent}.
     */
    private static final Map<Entity, Long> DISPEL = Collections.synchronizedMap(new WeakHashMap<>());

    private SpellSpawns() {
    }

    /** Отметить снаряд заклинания. */
    public static void markProjectile(Entity projectile) {
        projectile.getPersistentData().put(KEY, new CompoundTag());
    }

    /**
     * Отметить призванное существо.
     *
     * @param dispelAt игровое время исчезновения; не больше нуля — не исчезает
     */
    public static void markSummoned(Entity entity, long dispelAt) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_SUMMONED, true);
        if (dispelAt > 0) {
            tag.putLong(KEY_DISPEL_AT, dispelAt);
            DISPEL.put(entity, dispelAt);
        }
        entity.getPersistentData().put(KEY, tag);
    }

    /** Порождено ли заклинанием — снаряд или призванное существо. */
    public static boolean isSpellSpawned(Entity entity) {
        return entity.getPersistentData().contains(KEY);
    }

    private static boolean isSummoned(Entity entity) {
        return entity.getPersistentData().getCompound(KEY).getBoolean(KEY_SUMMONED);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide() && isSpellSpawned(entity)) {
            long dispelAt = entity.getPersistentData().getCompound(KEY).getLong(KEY_DISPEL_AT);
            if (dispelAt > 0) {
                DISPEL.put(entity, dispelAt);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0 || DISPEL.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        synchronized (DISPEL) {
            Iterator<Map.Entry<Entity, Long>> it = DISPEL.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Entity, Long> entry = it.next();
                Entity entity = entry.getKey();
                if (entity.isRemoved()) {
                    it.remove();
                } else if (entry.getValue() <= now) {
                    entity.discard();
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isSummoned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isSummoned(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
