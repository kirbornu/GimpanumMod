package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Предмет-Ядро, способный нести в себе настройку.
 *
 * <p>Средняя кнопка мыши по настроенному Ядру кладёт в руку именно такой
 * предмет, и поставленное из него Ядро сразу получает все привязки и теги.
 * Описание показывает, что именно предмет несёт, — иначе настроенные и пустые
 * Ядра в инвентаре не отличить.
 */
public class CoreBlockItem extends BlockItem {

    public CoreBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static CoreConfig storedConfig(ItemStack stack) {
        return stack.getOrDefault(GimpanumContent.CORE_CONFIG.get(), CoreConfig.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CoreConfig config = storedConfig(stack);
        if (config.isDefaultTemplate()) {
            tooltip.add(Component.translatable("item.gimpanum.core.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.gimpanum.core.configured")
                .withStyle(ChatFormatting.GOLD));

        int bound = config.boundPlayers().size();
        int crews = config.boundTeams().size();
        if (bound > 0 || crews > 0) {
            tooltip.add(Component.translatable("item.gimpanum.core.bound", bound, crews)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (!config.commands().isEmpty()) {
            tooltip.add(Component.translatable("item.gimpanum.core.commands", config.commands().size())
                    .withStyle(ChatFormatting.WHITE));
        }
        config.sealPostfix().ifPresent(postfix -> tooltip.add(
                Component.translatable("gimpanum.core.postfix", postfix).withStyle(ChatFormatting.GRAY)));

        tooltip.add(Component.translatable("item.gimpanum.core.tags",
                        yesNo(config.armed()), yesNo(config.invulnerable()),
                        yesNo(config.autoDisableInvulnerable()),
                        yesNo(config.sealEnabled()), yesNo(config.explosionEnabled()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "gimpanum.yes" : "gimpanum.no");
    }
}
