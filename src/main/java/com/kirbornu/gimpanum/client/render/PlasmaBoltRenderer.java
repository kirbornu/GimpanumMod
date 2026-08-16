package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.PlasmaBolt;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

/** Скелет, светящийся синим. Своя реализация по той же причине, что и у поглотителя. */
public class PlasmaBoltRenderer extends HumanoidMobRenderer<PlasmaBolt, SkeletonModel<PlasmaBolt>> {

    private static final ResourceLocation TEXTURE = Gimpanum.id("textures/entity/plasma_bolt.png");

    public PlasmaBoltRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON)), 0.5F);
    }

    /** Скелетья модель 1.95 в высоту — до четырёх блоков это чуть больше двух. */
    @Override
    protected void scale(PlasmaBolt entity, PoseStack pose, float partialTick) {
        pose.scale(2.05F, 2.05F, 2.05F);
    }

    @Override
    public ResourceLocation getTextureLocation(PlasmaBolt entity) {
        return TEXTURE;
    }
}
