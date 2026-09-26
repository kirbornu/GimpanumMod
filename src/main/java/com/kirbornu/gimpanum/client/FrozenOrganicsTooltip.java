package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
 * <p>Полный список под Shift: строк в нём несколько десятков, и развернуть его
 * без спроса значило бы закрыть подсказкой весь экран. Но и под Shift он
 * урезается до высоты экрана.
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

        // Шанс считается по предмету, а не по строке: один предмет может
        // стоять в конфиге дважды (пригоршней и штукой). Такой печатаем один
        // раз, иначе он вышел бы двумя строками с одним и тем же общим шансом.
        List<ThawedOrganics.Find> sorted = new ArrayList<>(finds);
        sorted.sort(Comparator.comparingInt(ThawedOrganics.Find::weight).reversed());
        List<ItemStack> kinds = new ArrayList<>();
        for (ThawedOrganics.Find find : sorted) {
            if (kinds.stream().noneMatch(item -> ItemStack.isSameItemSameComponents(item, find.item()))) {
                kinds.add(find.item());
            }
        }

        if (!Screen.hasShiftDown()) {
            lines.add(Component.translatable("gimpanum.tooltip.thawing_hint", kinds.size())
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        lines.add(Component.translatable("gimpanum.tooltip.thawing_header")
                .withStyle(ChatFormatting.GRAY));
        // Подсказку выше экрана игра не сжимает, а обрезает — вместе с
        // заголовком. Поэтому строк ровно столько, сколько влезает, а о
        // невлезших говорит последняя строка.
        int room = Math.max(1, visibleLines() - lines.size());
        int shown = kinds.size() <= room ? kinds.size() : Math.max(0, room - 1);
        for (ItemStack item : kinds.subList(0, shown)) {
            String chance = String.format(Locale.ROOT, "%.1f", ThawedOrganicsClient.chance(item) * 100.0F);
            lines.add(Component.translatable("gimpanum.tooltip.thawing_line",
                    item.getHoverName(), chance).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (shown < kinds.size()) {
            lines.add(Component.translatable("gimpanum.tooltip.thawing_more", kinds.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Сколько строк подсказки помещается на экран по высоте.
     *
     * <p>Строка подсказки занимает десять точек интерфейса, и ещё немного
     * уходит на рамку и отступ от края.
     */
    private static int visibleLines() {
        return (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 12) / 10;
    }
}
