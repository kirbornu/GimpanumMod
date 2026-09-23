package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.RaditaWingsItem;
import com.kirbornu.gimpanum.network.WingFlapPayload;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентская половина Крыльев Радитажа: заметить прыжок в полёте.
 *
 * <p>Взмах — это нажатие прыжка, а не удержание: иначе зажатый пробел махал
 * бы сам по себе до самой земли. И только если в прошлом тике уже летели —
 * тот прыжок, что раскрыл крылья, взмахом не считается.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class RaditaWingsClient {

    private static boolean wasJumping;
    private static boolean wasFlying;

    private RaditaWingsClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        // Та же подмена иконки на порванную, что у элитр: модель смотрит на это свойство.
        event.enqueueWork(() -> ItemProperties.register(GimpanumContent.RADITA_WINGS.get(),
                ResourceLocation.withDefaultNamespace("broken"),
                (stack, level, entity, seed) -> ElytraItem.isFlyEnabled(stack) ? 0.0F : 1.0F));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            wasJumping = false;
            wasFlying = false;
            return;
        }
        boolean jumping = player.input.jumping;
        boolean flying = player.isFallFlying();
        if (jumping && !wasJumping && flying && wasFlying) {
            ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
            if (wings.getItem() instanceof RaditaWingsItem && !player.getCooldowns().isOnCooldown(wings.getItem())) {
                PacketDistributor.sendToServer(WingFlapPayload.INSTANCE);
            }
        }
        wasJumping = jumping;
        wasFlying = flying;
    }
}
