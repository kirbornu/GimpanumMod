package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.entity.GimpanumEntities;
import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.tablet.GlyphItem;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;

/** Откуда берутся глифы и табличка: добыча некрофагов и рецепт. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProgressionGameTests {

    @GameTest(template = ARENA)
    public static void necrophagesDropGlyphs(GameTestHelper h) {
        // Плазменная молния роняет глиф с шансом 0,3: за 30 смертей ни одного —
        // примерно 2·10⁻⁵, то есть тест не мигает.
        for (int i = 0; i < 30; i++) {
            h.spawn(GimpanumEntities.PLASMA_BOLT.get(), new BlockPos(7, 3, 7)).kill();
        }
        h.runAfterDelay(2, () -> {
            Map<String, Integer> glyphs = new TreeMap<>();
            for (ItemEntity item : h.getEntities(EntityType.ITEM)) {
                ItemStack stack = item.getItem();
                if (stack.getItem() instanceof GlyphItem glyph) {
                    glyphs.merge(glyph.rune().id(), stack.getCount(), Integer::sum);
                }
            }
            log("glyph drops from 30 plasma bolts: " + glyphs);
            int total = glyphs.values().stream().mapToInt(Integer::intValue).sum();
            h.assertTrue(total >= 1, "ни одного глифа");
            h.assertTrue(total <= 30, "больше одного глифа за смерть: " + total);
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void tabletRecipe(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(Gimpanum.id("rune_tablet"));
        h.assertTrue(recipe.isPresent(), "рецепта нет");
        ItemStack m = new ItemStack(GimpanumContent.MONOLITHIC_CRYSTAL_ITEM.get());
        ItemStack e = new ItemStack(Items.ENDER_PEARL);
        List<ItemStack> grid = new ArrayList<>(List.of(
                ItemStack.EMPTY, m, ItemStack.EMPTY,
                m, e, m,
                ItemStack.EMPTY, m, ItemStack.EMPTY));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var found = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
        h.assertTrue(found.isPresent() && found.get().id().equals(Gimpanum.id("rune_tablet")), "не крафтится");
        ItemStack result = found.get().value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.is(MagicContent.TABLET.get()) && result.getCount() == 1, "не та табличка: " + result);

        grid.set(4, new ItemStack(Items.ENDER_EYE));
        h.assertTrue(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid),
                h.getLevel()).isEmpty(), "крафтится не из того");
        h.succeed();
    }
}
