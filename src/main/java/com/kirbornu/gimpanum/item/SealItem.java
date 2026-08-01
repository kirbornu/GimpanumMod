package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Печать — то, что остаётся от Ядра.
 *
 * <p>Хранит ники, привязанные к породившему её Ядру, и показывает их в
 * описании. Список снимается с Ядра в момент гибели и дальше неизменен.
 */
public class SealItem extends Item {

    public SealItem(Properties properties) {
        super(properties);
    }

    /** Ники, записанные в Печать; пусто, если Ядро было ни к кому не привязано. */
    public static List<String> boundPlayers(ItemStack stack) {
        return stack.getOrDefault(GimpanumContent.BOUND_PLAYERS.get(), List.of());
    }

    public static ItemStack create(List<String> boundPlayers) {
        ItemStack stack = new ItemStack(GimpanumContent.SEAL.get());
        stack.set(GimpanumContent.BOUND_PLAYERS.get(), List.copyOf(boundPlayers));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        List<String> bound = boundPlayers(stack);
        if (bound.isEmpty()) {
            tooltip.add(Component.translatable("item.gimpanum.seal.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.gimpanum.seal.bound")
                .withStyle(ChatFormatting.GRAY));
        for (String name : bound) {
            tooltip.add(Component.literal(" • " + name).withStyle(ChatFormatting.AQUA));
        }
    }
}
