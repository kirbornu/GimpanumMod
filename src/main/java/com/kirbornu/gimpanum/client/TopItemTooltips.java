package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.GodHeartbeatItem;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Строки подсказок, которым нужен смотрящий игрок, а не только сам предмет.
 *
 * <p>Сама подсказка предмета игрока не знает, а событие — знает.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class TopItemTooltips {

    private TopItemTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.has(GimpanumContent.FACETED.get())) {
            // Сразу под именем: это главное, что стоит знать о вещи.
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable("item.gimpanum.faceted").withStyle(ChatFormatting.AQUA));
        }
        Player player = event.getEntity();
        if (player != null && stack.getItem() instanceof GodHeartbeatItem) {
            event.getToolTip().add(Component.translatable("item.gimpanum.god_heartbeat.count",
                    GodHeartbeatItem.hearts(player)).withStyle(ChatFormatting.DARK_RED));
        }
    }
}
