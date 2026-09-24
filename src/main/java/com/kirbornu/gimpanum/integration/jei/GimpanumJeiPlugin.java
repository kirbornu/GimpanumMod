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
import java.util.List;
import java.util.Optional;

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

    /**
     * Рецепты находок, добавленные в категорию печи, — чтобы при новом списке
     * спрятать прежние, а не оставлять их рядом с новыми.
     */
    private final List<RecipeHolder<SmeltingRecipe>> inSmelting = new ArrayList<>();

    /** То же для «Обдува» Create. */
    private final List<RecipeHolder<SmeltingRecipe>> inFan = new ArrayList<>();

    /** Список, по которому собраны {@link #inSmelting}: с ним сверяется пришедший. */
    private List<ThawedOrganics.Find> shownFinds = List.of();

    /**
     * Номер сборки в именах рецептов. Спрятанный рецепт JEI помнит, поэтому
     * новые получают новые имена, а не повторяют старые.
     */
    private int generation;

    @Nullable
    private IJeiRuntime runtime;

    public GimpanumJeiPlugin() {
        ThawedOrganicsClient.onUpdate(this::sync);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Gimpanum.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        inSmelting.clear();
        inFan.clear();
        shownFinds = ThawedOrganicsClient.finds();
        List<RecipeHolder<SmeltingRecipe>> recipes = build(shownFinds);
        if (!recipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.SMELTING, recipes);
            inSmelting.addAll(recipes);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime value) {
        this.runtime = value;
        hideStub();
        sync();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.runtime = null;
        // Перечень строится заново — значит и добавлять придётся заново.
        inSmelting.clear();
        inFan.clear();
        shownFinds = List.of();
    }

    /**
     * Приводит показанное к текущему списку находок.
     *
     * <p>Список может прийти и после составления перечня JEI, и заново — после
     * {@code /gimpanum config reload}. Тогда прежние рецепты прячутся, а
     * новые встают на их место: правка файла обязана быть видна сразу, и
     * убранная из файла находка не должна висеть в JEI до перезахода. В
     * «Обдув» рецепты попадают здесь же: при регистрации эта категория ещё
     * не доступна.
     */
    private void sync() {
        if (runtime == null) {
            return;
        }
        Optional<RecipeType<RecipeHolder<SmeltingRecipe>>> fan = fanBlasting();
        List<ThawedOrganics.Find> finds = ThawedOrganicsClient.finds();
        if (!sameFinds(finds, shownFinds)) {
            if (!inSmelting.isEmpty()) {
                runtime.getRecipeManager().hideRecipes(RecipeTypes.SMELTING, List.copyOf(inSmelting));
                inSmelting.clear();
            }
            if (!inFan.isEmpty()) {
                fan.ifPresent(type -> runtime.getRecipeManager().hideRecipes(type, List.copyOf(inFan)));
                inFan.clear();
            }
            shownFinds = finds;
            List<RecipeHolder<SmeltingRecipe>> recipes = build(finds);
            if (!recipes.isEmpty()) {
                runtime.getRecipeManager().addRecipes(RecipeTypes.SMELTING, recipes);
                inSmelting.addAll(recipes);
            }
        }
        if (fan.isPresent() && inFan.isEmpty() && !inSmelting.isEmpty()) {
            runtime.getRecipeManager().addRecipes(fan.get(), List.copyOf(inSmelting));
            inFan.addAll(inSmelting);
        }
    }

    /** Совпадают ли два списка находок строка в строку: и вес, и предмет. */
    private static boolean sameFinds(List<ThawedOrganics.Find> a, List<ThawedOrganics.Find> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).weight() != b.get(i).weight() || !ItemStack.matches(a.get(i).item(), b.get(i).item())) {
                return false;
            }
        }
        return true;
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

    /** Собирает по рецепту печи на каждую находку списка. */
    private List<RecipeHolder<SmeltingRecipe>> build(List<ThawedOrganics.Find> finds) {
        Ingredient input = Ingredient.of(GimpanumContent.FROZEN_ORGANICS_ITEM.get());
        List<RecipeHolder<SmeltingRecipe>> recipes = new ArrayList<>();
        int batch = generation++;
        for (int i = 0; i < finds.size(); i++) {
            ResourceLocation id = Gimpanum.id("thawing/" + batch + "/" + i);
            ItemStack result = finds.get(i).item().copy();
            recipes.add(new RecipeHolder<>(id, new SmeltingRecipe(
                    "", CookingBookCategory.MISC, input, result, 0.7F, 200)));
        }
        return recipes;
    }
}
