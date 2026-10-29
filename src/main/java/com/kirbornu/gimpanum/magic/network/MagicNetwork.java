package com.kirbornu.gimpanum.magic.network;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Пакеты рунной магии. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class MagicNetwork {

    private MagicNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(MagicCooldownPayload.TYPE, MagicCooldownPayload.STREAM_CODEC,
                MagicCooldownPayload::handle);
    }
}
