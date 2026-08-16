package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиентские мелочи, без которых новая древесина не отрисуется. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class GimpanumClientSetup {

    private GimpanumClientSetup() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Без этого текстура таблички не попадёт в атлас и будет чёрно-розовой.
        event.enqueueWork(() -> Sheets.addWoodType(GimpanumContent.NEBULA_WOOD_TYPE));
    }
}
