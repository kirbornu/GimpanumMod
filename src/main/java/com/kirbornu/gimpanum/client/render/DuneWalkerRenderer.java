package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Зомби, выцветший до песочно-серого. */
public class DuneWalkerRenderer extends ZombieRenderer {

    private static final ResourceLocation TEXTURE = Gimpanum.id("textures/entity/dune_walker.png");

    public DuneWalkerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
