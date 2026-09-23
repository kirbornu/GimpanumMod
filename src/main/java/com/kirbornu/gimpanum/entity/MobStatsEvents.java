package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Откуда обитатели Гимпанума берут свои числа. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class MobStatsEvents {

    private MobStatsEvents() {
    }

    /**
     * Читаем до запуска мира.
     *
     * <p>Именно «до», а не «после»: числа нужны уже в тот миг, когда создаётся
     * первый моб, а память и темп удара уходят внутрь целей поведения при
     * создании и потом не меняются.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MobStats.load();
    }

    /**
     * Каждому мобу — его числа, при появлении в мире.
     *
     * <p>И при появлении, и при загрузке чанка: событие одно на оба случая, а
     * значит правка настройки доходит и до тех, кто уже стоял в мире.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) {
            MobStats.apply(living);
        }
    }
}
