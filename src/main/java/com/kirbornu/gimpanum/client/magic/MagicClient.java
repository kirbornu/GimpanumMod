package com.kirbornu.gimpanum.client.magic;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.MagicContent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Клиентская сторона магии: экран таблички и пустой рендерер сущности-заклинания. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class MagicClient {

    private MagicClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MagicContent.TABLET_MENU.get(), TabletScreen::new);
    }

    /** Сущность-заклинание невидима: всё, что от неё видно, — частицы с сервера. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MagicContent.SPELL.get(), NoopRenderer::new);
    }
}
