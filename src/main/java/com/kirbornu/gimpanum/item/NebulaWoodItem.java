package com.kirbornu.gimpanum.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Предмет небула-древесины: доски как доски, но не топливо.
 *
 * <p>Доски лежат в ванильном теге {@code minecraft:planks}, чтобы работать
 * везде, где игра ждёт доски. Тот же тег ванильно означает 300 тиков горения,
 * поэтому топливность приходится снимать отдельно: {@code 0} — «не топливо»,
 * в отличие от {@code -1}, которое означало бы «решай по-ванильному».
 *
 * <p>Дерево, выросшее там, где нечему гореть, гореть и не должно.
 */
public class NebulaWoodItem extends BlockItem {

    public NebulaWoodItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return 0;
    }
}
