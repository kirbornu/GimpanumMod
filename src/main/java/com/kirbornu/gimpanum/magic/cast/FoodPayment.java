package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.config.MagicSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * Оплата едой: сначала насыщенность, потом уровень еды, потом здоровье.
 *
 * <p>Уровень еды в игре целый, а цена может быть дробной (насыщенность —
 * дробная). Когда с уровня еды приходится снять больше, чем осталось
 * заплатить, переплата возвращается насыщенностью — так ничего не теряется.
 *
 * <p>Если цена доходит до здоровья и убила бы игрока, а смертельная цена в
 * настройке запрещена, каст не происходит и ничего не списывается. В
 * творческом режиме магия бесплатна, как и всё остальное.
 */
public final class FoodPayment implements SpellPayment {

    @Override
    public boolean tryPay(ServerPlayer player, int runes, MagicSettings settings) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        MagicSettings.Cost cost = settings.cost();
        double price = cost.foodPerRune() * runes;
        FoodData food = player.getFoodData();
        double fromSaturation = Math.min(food.getSaturationLevel(), price);
        double left = price - fromSaturation;
        double fromFood = Math.min(food.getFoodLevel(), left);
        double health = (left - fromFood) * cost.healthPerFood();
        if (health > 0 && !cost.canKill() && health >= player.getHealth()) {
            return false;
        }

        food.setSaturation((float) (food.getSaturationLevel() - fromSaturation));
        int taken = (int) Math.ceil(fromFood - 1.0E-9);
        food.setFoodLevel(food.getFoodLevel() - taken);
        double overpaid = taken - fromFood;
        if (overpaid > 0) {
            food.setSaturation((float) Math.min(food.getFoodLevel(), food.getSaturationLevel() + overpaid));
        }
        if (health > 0) {
            if (health >= player.getHealth()) {
                // Смертельная цена разрешена: умирает от собственной магии.
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            } else {
                player.setHealth((float) (player.getHealth() - health));
            }
        }
        return true;
    }
}
