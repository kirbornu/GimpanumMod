package com.kirbornu.gimpanum.cannons;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileRenderer;

/**
 * Клиентская половина Яростного снаряда — один отрисовщик.
 *
 * <p>Отрисовщик берём чужой: он рисует снаряд тем блоком, из которого тот
 * вылетел, а блок у нас свой. Ничего дописывать не требуется.
 */
public final class BigCannonsClient {

    private BigCannonsClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BigCannons.FURIOUS_SHELL_PROJECTILE.get(),
                BigCannonProjectileRenderer::new);
    }
}
