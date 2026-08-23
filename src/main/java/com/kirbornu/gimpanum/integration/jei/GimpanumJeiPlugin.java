package com.kirbornu.gimpanum.integration.jei;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.client.ThawedOrganicsClient;
import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Подключение к JEI.
 *
 * <p>JEI необязателен, и на класс с его типами нигде больше нет ссылок:
 * находит его сам JEI по аннотации {@link JeiPlugin}, а без JEI класс просто
 * не загружается. Та же изоляция, что у мостов к Sable и Create Big Cannons.
 *
 * <p>Своей категории у оттаивания нет и не должно быть: это обычная
 * переплавка в печи, и отдельный экран внушал бы игроку, что нужен какой-то
 * особый станок. Вместо этого настоящий рецепт — тот, что показывает одну
 * заглушку из json, — прячется, а на его место в ту же категорию печи встаёт
 * по рецепту на каждую находку.
 *
 * <p>Та же подмена делается и в «Обдуве» Create. Свои записи Create собирает
 * сам, обходя настоящие рецепты переплавки, поэтому в его экране до этой
 * правки тоже стояла заглушка. Категория адресуется строкой
 * {@code create:fan_blasting} и мягко пропускается, если Create нет или он
 * переименовал её: это украшение, а не работоспособность.
 */
@JeiPlugin
public class GimpanumJeiPlugin implements IModPlugin {

    /** Имя настоящего рецепта — того, что надо спрятать. */
    private static final ResourceLocation REAL_RECIPE = Gimpanum.id("frozen_organics");

    /** Категория «Обдув» Create: те же рецепты переплавки, свой экран. */
    private static final ResourceLocation FAN_BLASTING =
            ResourceLocation.fromNamespaceAndPath("create", "fan_blasting");

    /** Уже показанные находки — чтобы досылка не наплодила повторов. */
    private final Set<ResourceLocation> shown = new HashSet<>();

    @Nullable
    private IJeiRuntime runtime;

    public GimpanumJeiPlugin() {
        ThawedOrganicsClient.onUpdate(this::pushLate);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Gimpanum.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<SmeltingRecipe>> recipes = build();
        if (!recipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.SMELTING, recipes);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime value) {
        this.runtime = value;
        hideStub();
        pushLate();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.runtime = null;
        // Перечень строится заново — значит и добавлять придётся заново.
        shown.clear();
    }

    /** Досылает то, что пришло уже после составления перечня. */
    private void pushLate() {
        if (runtime == null) {
            return;
        }
        List<RecipeHolder<SmeltingRecipe>> recipes = build();
        if (recipes.isEmpty()) {
            return;
        }
        runtime.getRecipeManager().addRecipes(RecipeTypes.SMELTING, recipes);
        fanBlasting().ifPresent(type -> runtime.getRecipeManager().addRecipes(type, recipes));
    }

    /**
     * Прячет рецепт-заглушку в обеих категориях.
     *
     * <p>Без этого рядом с полусотней честных находок остался бы рецепт с
     * единственным предметом — и именно он попадался бы игроку первым.
     */
    private void hideStub() {
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || minecraft.level == null) {
            return;
        }
        minecraft.level.getRecipeManager().byKey(REAL_RECIPE)
                .filter(holder -> holder.value() instanceof SmeltingRecipe)
                .ifPresent(holder -> {
                    @SuppressWarnings("unchecked")
                    List<RecipeHolder<SmeltingRecipe>> stub =
                            List.of((RecipeHolder<SmeltingRecipe>) holder);
                    runtime.getRecipeManager().hideRecipes(RecipeTypes.SMELTING, stub);
                    fanBlasting().ifPresent(type ->
                            runtime.getRecipeManager().hideRecipes(type, stub));
                });
    }

    /**
     * Категория «Обдув» Create, если она есть.
     *
     * <p>Собрана она на {@code AbstractCookingRecipe} — на то же, что и
     * ванильная печь, — поэтому наши записи ей подходят без переделки.
     * Приведение непроверяемое: тип категории известен только по её имени.
     */
    @SuppressWarnings("unchecked")
    private Optional<RecipeType<RecipeHolder<SmeltingRecipe>>> fanBlasting() {
        if (runtime == null) {
            return Optional.empty();
        }
        try {
            return runtime.getRecipeManager().getRecipeType(FAN_BLASTING)
                    .map(type -> (RecipeType<RecipeHolder<SmeltingRecipe>>) type);
        } catch (RuntimeException failure) {
            Gimpanum.LOGGER.debug("Категория «Обдув» Create не найдена, оттаивание в ней не показано", failure);
            return Optional.empty();
        }
    }

    /** Собирает по рецепту печи на каждую ещё не показанную находку. */
    private List<RecipeHolder<SmeltingRecipe>> build() {
        Ingredient input = Ingredient.of(GimpanumContent.FROZEN_ORGANICS_ITEM.get());
        List<RecipeHolder<SmeltingRecipe>> recipes = new ArrayList<>();
        List<ThawedOrganics.Find> finds = ThawedOrganicsClient.finds();

        for (int i = 0; i < finds.size(); i++) {
            ResourceLocation id = Gimpanum.id("thawing/" + i);
            if (!shown.add(id)) {
                continue;
            }
            ItemStack result = finds.get(i).item().copy();
            recipes.add(new RecipeHolder<>(id, new SmeltingRecipe(
                    "", CookingBookCategory.MISC, input, result, 0.7F, 200)));
        }
        return recipes;
    }
}
