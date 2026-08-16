package com.kirbornu.gimpanum;

import com.kirbornu.gimpanum.entity.GimpanumEntities;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Gimpanum.MOD_ID)
public class Gimpanum {

    public static final String MOD_ID = "gimpanum";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gimpanum(IEventBus modBus, ModContainer container) {
        GimpanumContent.register(modBus);
        GimpanumEntities.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
