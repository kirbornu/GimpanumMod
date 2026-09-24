package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.WeakHashMap;

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
     * Сколько раз печь NeoForge спрашивает {@code assemble} в тот такт, когда
     * порция готова: {@code canBurn} в тике печи, затем {@code canBurn} внутри
     * {@code burn} и сам {@code assemble} внутри {@code burn}. В обычный такт
     * вопрос один, в такт, когда печь разжигается заново, — два.
     */
    private static final int CALLS_WHEN_BURNED = 3;

    /**
     * Находка текущей порции — по стопке во входной ячейке печи.
     *
     * <p>Ответ обязан быть одним и тем же, пока порция не готова: печь каждый
     * такт сверяет его с выходной ячейкой, и ответ, меняющийся от такта к
     * такту, то пропускал бы порцию, то нет, а на последнем такте проверка
     * одобрила бы одно, а выдача положила бы другое. И обязан меняться, когда
     * порция готова, — иначе все порции были бы одинаковыми.
     *
     * <p>Раньше порцию отличали по числу предметов во входной ячейке. Этого
     * мало: воронка доливает органику сразу после переплавки, число
     * возвращается прежним, и печь на автоматической подаче выдавала одну и
     * ту же находку без конца. Поэтому готовность порции определяется по
     * самому признаку переплавки — по трём вопросам за один такт.
     *
     * <p>Ключ — сама стопка: у {@link ItemStack} нет равенства по значению,
     * поэтому карта сравнивает по тождеству, а слабые ссылки отпускают стопки,
     * которых больше нет ни в одной печи.
     */
    private static final ThreadLocal<Map<ItemStack, Portion>> PORTIONS = ThreadLocal.withInitial(WeakHashMap::new);

    private static final class Portion {
        final ItemStack result;
        long tick;
        int calls;

        Portion(ItemStack result, long tick) {
            this.result = result;
            this.tick = tick;
        }
    }

    public ThawingRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                         ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // Без сервера печей нет — спрашивает разве что просмотрщик.
            return roll(input, registries);
        }
        long now = server.getTickCount();
        ItemStack sample = input.item();
        Map<ItemStack, Portion> portions = PORTIONS.get();
        Portion portion = portions.get(sample);
        if (portion == null || portion.tick != now && portion.calls >= CALLS_WHEN_BURNED) {
            portion = new Portion(roll(input, registries), now);
            portions.put(sample, portion);
        }
        if (portion.tick != now) {
            portion.tick = now;
            portion.calls = 0;
        }
        portion.calls++;
        return portion.result.copy();
    }

    private ItemStack roll(SingleRecipeInput input, HolderLookup.Provider registries) {
        return ThawedOrganics.roll(RANDOM.get()).orElseGet(() -> super.assemble(input, registries));
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
