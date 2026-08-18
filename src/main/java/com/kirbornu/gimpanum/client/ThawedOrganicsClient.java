package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Клиентский список находок Замороженной органики.
 *
 * <p>Зеркало серверного конфига, присланное пакетом. Нужно затем, чтобы
 * просмотрщик рецептов показывал настоящее содержимое, а не заглушку из json.
 * Пустой список до прихода пакета — обычное дело: экран рецепта откроют
 * заведомо позже входа в мир.
 */
public final class ThawedOrganicsClient {

    private static List<ThawedOrganics.Find> finds = List.of();

    private ThawedOrganicsClient() {
    }

    public static void accept(List<ThawedOrganics.Find> fresh) {
        finds = List.copyOf(fresh);
    }

    /** Находки с положительным весом: нулевой вес выключает строку в конфиге. */
    public static List<ThawedOrganics.Find> finds() {
        return finds.stream().filter(find -> find.weight() > 0).toList();
    }

    public static List<ItemStack> items() {
        return finds().stream().map(find -> find.item().copy()).toList();
    }

    /**
     * Доля этого предмета среди всех находок, от нуля до единицы.
     *
     * <p>Складываются веса всех строк с таким предметом, а не берётся первая
     * попавшаяся: один и тот же предмет может стоять в конфиге дважды — скажем,
     * отдельной строкой на пригоршню и отдельной на штуку.
     */
    public static float chance(ItemStack sample) {
        int total = 0;
        int matched = 0;
        for (ThawedOrganics.Find find : finds()) {
            total += find.weight();
            if (ItemStack.isSameItemSameComponents(find.item(), sample)) {
                matched += find.weight();
            }
        }
        return total <= 0 ? 0.0F : (float) matched / total;
    }
}
