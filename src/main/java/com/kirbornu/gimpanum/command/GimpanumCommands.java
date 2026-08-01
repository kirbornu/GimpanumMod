package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.Gimpanum;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class GimpanumCommands {

    private GimpanumCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CoreCommand.register(event.getDispatcher());
    }
}
