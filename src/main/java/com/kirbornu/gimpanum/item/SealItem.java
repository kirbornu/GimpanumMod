package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Хрустальная печать — то, что остаётся от Ядра.
 *
 * <p>Хранит привязки породившего её Ядра и показывает их в описании: отдельных
 * игроков и экипажи с составами. Приставка из настройки Ядра попадает в само
 * название предмета — обычно это название экипажа, чьё Ядро было уничтожено.
 */
public class SealItem extends Item {

    /**
     * Час до исчезновения вместо ванильных пяти минут.
     *
     * <p>Задано на предмете, а не на выброшенной сущности, чтобы Печать жила
     * одинаково долго независимо от того, откуда она взялась: из погибшего Ядра,
     * из периодической выдачи или просто выброшена игроком из инвентаря.
     */
    private static final int LIFESPAN_TICKS = 60 * 60 * 20;

    public SealItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return LIFESPAN_TICKS;
    }

    public static SealContents contents(ItemStack stack) {
        return stack.getOrDefault(GimpanumContent.SEAL_CONTENTS.get(), SealContents.EMPTY);
    }

    public static ItemStack create(SealContents contents) {
        ItemStack stack = new ItemStack(GimpanumContent.SEAL.get());
        stack.set(GimpanumContent.SEAL_CONTENTS.get(), contents);
        return stack;
    }

    /**
     * Приставка дописывается к названию здесь, а не через CUSTOM_NAME: так
     * название остаётся переводимым и не выводится курсивом, как переименованные
     * в наковальне предметы.
     */
    @Override
    public Component getName(ItemStack stack) {
        return contents(stack).postfix()
                .map(postfix -> (Component) Component.translatable(getDescriptionId())
                        .append(Component.literal(" \"" + postfix + "\"")))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        SealContents contents = contents(stack);
        tooltip.add(Component.translatable("item.gimpanum.seal.price", contents.price())
                .withStyle(ChatFormatting.GOLD));

        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("item.gimpanum.seal.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (!contents.players().isEmpty()) {
            tooltip.add(Component.translatable("item.gimpanum.seal.bound")
                    .withStyle(ChatFormatting.GRAY));
            for (String name : contents.players()) {
                tooltip.add(Component.literal(" • " + name).withStyle(ChatFormatting.AQUA));
            }
        }

        for (BoundTeam team : contents.teams()) {
            tooltip.add(Component.translatable("item.gimpanum.seal.team", team.teamName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (String name : team.members()) {
                tooltip.add(Component.literal(" • " + name).withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
