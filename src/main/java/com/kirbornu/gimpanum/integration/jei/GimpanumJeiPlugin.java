package com.kirbornu.gimpanum.integration.jei;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.List;

/**
 * Подключение к JEI.
 *
 * <p>JEI необязателен, и на класс с его типами нигде больше нет ссылок:
 * находит его сам JEI по аннотации {@link JeiPlugin}, а без JEI класс просто
 * не загружается. Та же изоляция, что у мостов к Sable и Create Big Cannons.
 */
@JeiPlugin
public class GimpanumJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return Gimpanum.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ThawingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Ровно одна запись: рецепт оттаивания один, а разными его делает
        // случайный выход, который перебирается внутри выходного слота.
        registration.addRecipes(ThawingCategory.TYPE, List.of(new ThawingCategory.Display()));
    }

    /**
     * Печь — в шапке экрана.
     *
     * <p>Оттаивание идёт обычной переплавкой, поэтому в графе рецептов оно
     * обязано находиться от печи так же, как ванильные рецепты.
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(Items.FURNACE),
                ThawingCategory.TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(Items.BLAST_FURNACE),
                ThawingCategory.TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK,
                new ItemStack(GimpanumContent.FROZEN_ORGANICS.get()), ThawingCategory.TYPE);
    }

    /**
     * Прячет оттаивание из обычной печи.
     *
     * <p>Рецепт настоящий и в печи работает, поэтому JEI показывает его в
     * ванильной категории — с тем единственным предметом, что стоит в json
     * заглушкой. Рядом с нашим экраном это выглядело бы как два
     * противоречащих друг другу рецепта, и правым оказался бы неверный.
     *
     * <p>Прячем именно здесь: раньше рецептов у клиента ещё нет, они приходят
     * с сервера.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Minecraft.getInstance().level.getRecipeManager()
                .byKey(Gimpanum.id("frozen_organics"))
                .filter(holder -> holder.value() instanceof SmeltingRecipe)
                .ifPresent(holder -> runtime.getRecipeManager().hideRecipes(
                        RecipeTypes.SMELTING, List.of((RecipeHolder<SmeltingRecipe>) holder)));
    }
}
