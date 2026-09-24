package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Шансы находок в подсказке Замороженной органики.
 *
 * <p>В экране печи их разместить негде — там одна ячейка выхода, — а знать их
 * игроку надо: разница между тремя процентами и одной десятой решает, стоит
 * ли копать ради находки. Раньше это показывал отдельный экран «Оттаивание»,
 * но своя категория внушала, будто нужен особый станок, а никакого станка
 * нет — обычная печь.
 *
 * <p>Полный список под Shift: строк в нём шесть десятков, и развернуть его
 * целиком значило бы закрыть подсказкой весь экран.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID, value = Dist.CLIENT)
public final class FrozenOrganicsTooltip {

    private FrozenOrganicsTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(GimpanumContent.FROZEN_ORGANICS_ITEM.get())) {
            return;
        }
        List<ThawedOrganics.Find> finds = ThawedOrganicsClient.finds();
        if (finds.isEmpty()) {
            return;
        }
        List<Component> lines = event.getToolTip();

        if (!Screen.hasShiftDown()) {
            lines.add(Component.translatable("gimpanum.tooltip.thawing_hint", finds.size())
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        lines.add(Component.translatable("gimpanum.tooltip.thawing_header")
                .withStyle(ChatFormatting.GRAY));
        List<ThawedOrganics.Find> sorted = new ArrayList<>(finds);
        sorted.sort(Comparator.comparingInt(ThawedOrganics.Find::weight).reversed());
        // Шанс считается по предмету, а не по строке: один предмет может
        // стоять в конфиге дважды (пригоршней и штукой). Такой печатаем один
        // раз, иначе он вышел бы двумя строками с одним и тем же общим шансом.
        List<ItemStack> printed = new ArrayList<>();
        for (ThawedOrganics.Find find : sorted) {
            if (printed.stream().anyMatch(item -> ItemStack.isSameItemSameComponents(item, find.item()))) {
                continue;
            }
            printed.add(find.item());
            String chance = String.format(Locale.ROOT, "%.1f",
                    ThawedOrganicsClient.chance(find.item()) * 100.0F);
            lines.add(Component.translatable("gimpanum.tooltip.thawing_line",
                    find.item().getHoverName(), chance).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
