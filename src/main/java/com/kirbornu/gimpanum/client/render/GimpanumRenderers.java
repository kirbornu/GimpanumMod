package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.GimpanumEntities;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
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
        // Та же модель и текстура: детский рост и броню зомби рисует сам.
        event.registerEntityRenderer(GimpanumEntities.DUNE_CAPTAIN.get(), DuneWalkerRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.SPACE_DEVOURER.get(), SpaceDevourerRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.PLASMA_BOLT.get(), PlasmaBoltRenderer::new);
        // Воспоминание невидимо: всё, что от него видно, — искры с сервера.
        event.registerEntityRenderer(GimpanumEntities.MEMORY.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(GimpanumEntities.PLASMA_PROJECTILE.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.5F, true));
    }

    /** Крылья Радитажа — на игроках и на стойках для брони, как элитры. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new RaditaWingsLayer<>(renderer, event.getEntityModels()));
            }
        }
        ArmorStandRenderer stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand != null) {
            stand.addLayer(new RaditaWingsLayer<>(stand, event.getEntityModels()));
        }
    }
}
