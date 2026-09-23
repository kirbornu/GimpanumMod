package com.kirbornu.gimpanum.client.render;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.RaditaWingsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Крылья Радитажа на спине — модель элитр со своей текстурой.
 *
 * <p>Не наследник ванильного слоя элитр нарочно: тот подменяет текстуру
 * плащом игрока, если плащ есть, а у Крыльев вид свой всегда.
 */
public class RaditaWingsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gimpanum.MOD_ID, "textures/entity/radita_wings.png");

    private final ElytraModel<T> model;

    public RaditaWingsLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.model = new ElytraModel<>(models.bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        ItemStack wings = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(wings.getItem() instanceof RaditaWingsItem)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        this.getParentModel().copyPropertiesTo(model);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                RenderType.armorCutoutNoCull(TEXTURE), wings.hasFoil());
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
