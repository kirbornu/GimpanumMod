package com.kirbornu.gimpanum.client.magic;

import com.kirbornu.gimpanum.magic.tablet.TabletMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Экран таблички в сером ванильном стиле.
 *
 * <p>Рисуется прямоугольниками, а не текстурой: сетка меняет размер от 2×2
 * до 7×7, и одна картинка на все сорок девять вариантов не годится. Цвета —
 * ванильные: серый фон, тёмные ячейки со светлой нижней кромкой, как у
 * сундука.
 */
public class TabletScreen extends AbstractContainerScreen<TabletMenu> {

    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int LABEL = 0xFF404040;

    public TabletScreen(TabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = TabletMenu.WIDTH;
        this.imageHeight = TabletMenu.HEIGHT;
        this.inventoryLabelX = TabletMenu.INVENTORY_X;
        this.inventoryLabelY = TabletMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                slot(graphics, x + slot.x - 1, y + slot.y - 1);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.gimpanum.rune_tablet.rows", menu.rows()),
                TabletMenu.UPGRADE_LABEL_X, TabletMenu.ROW_UPGRADE_Y - 10, LABEL, false);
        graphics.drawString(font, Component.translatable("gui.gimpanum.rune_tablet.columns", menu.columns()),
                TabletMenu.UPGRADE_LABEL_X, TabletMenu.COLUMN_UPGRADE_Y - 10, LABEL, false);
    }

    /** Панель с чёрным контуром и объёмной кромкой. */
    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y, x + width - 1, y + height, OUTLINE);
        graphics.fill(x, y + 1, x + width, y + height - 1, OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, LIGHT);
        graphics.fill(x + 3, y + 3, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, BACKGROUND);
    }

    /** Ячейка 18×18: тёмная верхне-левая кромка, светлая нижне-правая. */
    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }
}
