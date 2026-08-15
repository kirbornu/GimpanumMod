package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.Optional;

/**
 * Переплавка с непредсказуемым выходом.
 *
 * <p>Нужна ровно для одного: Замороженная органика в печи оттаивает, и что
 * именно в ней замёрзло, выясняется только сейчас. Наследуемся от
 * {@link SmeltingRecipe}, а не заводим свой тип рецепта, — тогда обычная печь
 * и JEI находят рецепт сами, отличается только сериализатор.
 *
 * <p>Список возможных находок держится в теге предметов
 * {@code gimpanum:thawed_organics}, а не в коде: так сборка может дополнять
 * его своими растениями, не трогая мод.
 *
 * <p>Оговорка про печь: {@code assemble} вызывается ещё и в проверке
 * «поместится ли результат», поэтому выход в слоте и следующая находка обычно
 * не совпадут — печь встанет, пока слот не освободят. Воронка или туннель
 * Create забирают результат сразу, и заминки не будет.
 */
public class ThawingRecipe extends SmeltingRecipe {

    /** Что может оказаться внутри. Пустой тег — вернётся результат из рецепта. */
    public static final TagKey<Item> RESULTS = TagKey.create(Registries.ITEM, Gimpanum.id("thawed_organics"));

    private static final RandomSource RANDOM = RandomSource.create();

    public ThawingRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                         ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        Optional<HolderSet.Named<Item>> pool = registries.lookupOrThrow(Registries.ITEM).get(RESULTS);
        return pool.flatMap(set -> set.getRandomElement(RANDOM))
                .<ItemStack>map(ItemStack::new)
                .orElseGet(() -> super.assemble(input, registries));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GimpanumContent.THAWING.get();
    }
}
