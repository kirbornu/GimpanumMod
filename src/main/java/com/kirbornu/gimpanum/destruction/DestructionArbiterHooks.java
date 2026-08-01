package com.kirbornu.gimpanum.destruction;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Заводит часы арбитра от тика сервера. */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class DestructionArbiterHooks {

    private DestructionArbiterHooks() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DestructionArbiter.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DestructionArbiter.reset();
    }
}
