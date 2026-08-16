package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.SpaceDevourer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Паучья модель в полтора раза крупнее и без единого светлого пятна.
 *
 * <p>Свой рендерер, а не ванильный {@code SpiderRenderer}: тот привязан к
 * классу паука, а поглотитель наследуется от обычного враждебного моба, чтобы
 * не тащить паучьи повадки.
 */
public class SpaceDevourerRenderer extends MobRenderer<SpaceDevourer, SpiderModel<SpaceDevourer>> {

    private static final ResourceLocation TEXTURE = Gimpanum.id("textures/entity/space_devourer.png");
    private static final float SCALE = 1.5F;

    public SpaceDevourerRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 1.1F);
    }

    @Override
    protected void scale(SpaceDevourer entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(SpaceDevourer entity) {
        return TEXTURE;
    }
}
