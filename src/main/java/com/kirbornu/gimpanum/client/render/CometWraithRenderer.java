package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.client.renderer.entity.AllayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.allay.Allay;

/** Аллай целиком, кроме шкуры. */
public class CometWraithRenderer extends AllayRenderer {

    private static final ResourceLocation TEXTURE = Gimpanum.id("textures/entity/comet_wraith.png");

    public CometWraithRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Allay entity) {
        return TEXTURE;
    }
}
