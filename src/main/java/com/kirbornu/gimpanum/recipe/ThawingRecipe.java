package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * Переплавка с непредсказуемым выходом.
 *
 * <p>Нужна ровно для одного: Замороженная органика в печи оттаивает, и что
 * именно в ней замёрзло, выясняется только сейчас. Наследуемся от
 * {@link SmeltingRecipe}, а не заводим свой тип рецепта, — тогда обычная печь
 * и просмотрщики рецептов находят рецепт сами, отличается только сериализатор.
 *
 * <p>Список находок держит {@link ThawedOrganics} — файл в конфиге сервера.
 * Если список пуст (не прочитался, либо это клиент выделенного сервера, где
 * файла нет), возвращается результат, записанный в самом рецепте.
 *
 * <p>Случайный выход подставляется в двух местах, и это не перестраховка.
 * Ванильная печь зовёт {@code assemble}, а вот всё, что автоматизирует
 * переплавку со стороны, обычно берёт {@code getResultItem} — так поступает
 * и Create в обдуве вентилятором над горелкой
 * ({@code RecipeApplier.applyRecipeOn}). Переопредели мы только
 * {@code assemble} — Create выдавал бы одну и ту же строчку из json, и
 * случайность работала бы лишь в печи.
 */
public class ThawingRecipe extends SmeltingRecipe {

    /** По потоку: результат спрашивают и серверный тик, и клиент в просмотрщике. */
    private static final ThreadLocal<RandomSource> RANDOM = ThreadLocal.withInitial(RandomSource::create);

    /**
     * Последняя выданная находка — чтобы два вопроса об одной и той же порции
     * получили один ответ.
     *
     * <p>Ванильная печь спрашивает {@code assemble} дважды за такт: сперва в
     * {@code canBurn} («поместится ли результат»), потом в {@code burn} («что
     * положить»). Отвечай мы разное, случалось бы худшее: проверка одобряет
     * выход, совпавший с содержимым выходного слота, а выдача бросает жребий
     * заново, не совпадает — и {@code burn} молча съедает вход, ничего не
     * положив. Ключ — порция вместе с её остатком, поэтому в пределах одной
     * переплавки ответ один, а на следующей единице сырья жребий бросается
     * заново.
     */
    private static final ThreadLocal<Roll> LAST_ROLL = new ThreadLocal<>();

    private record Roll(int stackIdentity, int count, ItemStack result) {
    }

    public ThawingRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                         ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack sample = input.item();
        int identity = System.identityHashCode(sample);
        int count = sample.getCount();

        Roll cached = LAST_ROLL.get();
        if (cached != null && cached.stackIdentity() == identity && cached.count() == count) {
            return cached.result().copy();
        }
        ItemStack rolled = ThawedOrganics.roll(RANDOM.get())
                .orElseGet(() -> super.assemble(input, registries));
        LAST_ROLL.set(new Roll(identity, count, rolled));
        return rolled.copy();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ThawedOrganics.roll(RANDOM.get())
                .orElseGet(() -> super.getResultItem(registries));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GimpanumContent.THAWING.get();
    }
}
