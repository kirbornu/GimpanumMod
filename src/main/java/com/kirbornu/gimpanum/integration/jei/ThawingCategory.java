package com.kirbornu.gimpanum.integration.jei;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.client.ThawedOrganicsClient;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Экран рецепта «Оттаивание».
 *
 * <p>Своя категория, а не запись в печи, потому что у Замороженной органики
 * выход случайный, а обычная запись рецепта показывает ровно один предмет —
 * тот, что стоит в json заглушкой. Здесь выходной слот перебирает все
 * возможные находки, и у каждой в подсказке стоит её шанс.
 *
 * <p>Список читается прямо в {@link #setRecipe} — не сохраняется в объект
 * рецепта. Так надо: список приходит с сервера пакетом, и когда JEI
 * составляет свой перечень категорий, он может ещё не дойти. Зато к моменту,
 * когда игрок откроет экран, он есть заведомо.
 */
public class ThawingCategory implements IRecipeCategory<ThawingCategory.Display> {

    /** Экран у категории один: рецепт оттаивания тоже один. */
    public record Display() {
    }

    public static final RecipeType<Display> TYPE =
            RecipeType.create(Gimpanum.MOD_ID, "thawing", Display.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 42;

    private final IDrawable icon;
    private final IDrawable arrow;

    public ThawingCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemLike(GimpanumContent.FROZEN_ORGANICS.get());
        this.arrow = helper.getRecipeArrow();
    }

    @Override
    public RecipeType<Display> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gimpanum.jei.thawing");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    // Фона у категории нет, поэтому размеры задаются здесь: иначе те же методы
    // по умолчанию спросят фон и бросят исключение.
    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Display recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 13)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(GimpanumContent.FROZEN_ORGANICS.get()));

        List<ItemStack> finds = ThawedOrganicsClient.items();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 62, 13)
                .setOutputSlotBackground()
                .addItemStacks(finds)
                // Шанс — в подсказке самого предмета: слот перебирает находки
                // по очереди, и подписать их все разом рядом со слотом негде.
                .addRichTooltipCallback((slot, tooltip) -> slot.getDisplayedItemStack().ifPresent(shown -> {
                    float chance = ThawedOrganicsClient.chance(shown);
                    if (chance > 0.0F) {
                        tooltip.add(Component.translatable("gimpanum.jei.chance",
                                        String.format(Locale.ROOT, "%.1f", chance * 100.0F))
                                .withStyle(ChatFormatting.GRAY));
                    }
                }));
    }

    @Override
    public void draw(Display recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 32, 14);

        int variants = ThawedOrganicsClient.finds().size();
        Component line = variants == 0
                ? Component.translatable("gimpanum.jei.no_data")
                : Component.translatable("gimpanum.jei.variants", variants);
        graphics.drawString(Minecraft.getInstance().font, line, 6, 2, 0xFF808080, false);
    }
}
