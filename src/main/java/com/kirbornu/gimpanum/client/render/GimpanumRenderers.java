package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.GimpanumEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Отрисовка обитателей Гимпанума — на ванильных моделях, со своими текстурами. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class GimpanumRenderers {

    private GimpanumRenderers() {
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GimpanumEntities.COMET_WRAITH.get(), CometWraithRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.DUNE_WALKER.get(), DuneWalkerRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.SPACE_DEVOURER.get(), SpaceDevourerRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.PLASMA_BOLT.get(), PlasmaBoltRenderer::new);
    }
}
