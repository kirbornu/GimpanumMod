package com.kirbornu.gimpanum.lore;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.NecrophageEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Откуда лор попадает к игрокам.
 *
 * <p>Из некрофагов — редко и не таблицей добычи: таблица умеет выдавать
 * заранее известный предмет, а книга каждый раз разная, и собирается она в
 * коде. Поэтому — событие выпадения.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class LoreEvents {

    /** Как часто некрофаг уносит с собой чью-то книгу. */
    private static final float CHANCE = 0.03F;

    private LoreEvents() {
    }

    /** Папка читается на старте сервера, рядом с настройками конвертеров. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LoreBooks.load();
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !dead.getType().is(NecrophageEvents.NECROPHAGE)) {
            return;
        }
        if (dead.getRandom().nextFloat() >= CHANCE) {
            return;
        }
        LoreBooks.roll(dead.getRandom()).ifPresent(book -> event.getDrops().add(
                new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), book)));
    }
}
