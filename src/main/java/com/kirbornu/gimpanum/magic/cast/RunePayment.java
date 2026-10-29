package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.config.MagicSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Оплата рун: едой и хрусталём, обе части сразу.
 *
 * <p>Еда: сначала насыщенность, потом уровень еды, потом здоровье. Уровень
 * еды в игре целый, а цена может быть дробной (насыщенность — дробная).
 * Когда с уровня еды приходится снять больше, чем осталось заплатить,
 * переплата возвращается насыщенностью — так ничего не теряется.
 *
 * <p>Хрусталь: один предмет за каждые начатые {@code runesPerCrystal} рун.
 * Берётся сперва из основного инвентаря и лишь потом из второй руки: там
 * обычно лежит осколок-катализатор, и съесть его ценой раньше прочих было бы
 * подлостью.
 *
 * <p>Сначала проверяется всё, потом списывается всё. Не хватает хоть чего-то
 * — не списывается ничего: нельзя отдать хрусталь за заклинание, на которое
 * не хватило еды. Если цена доходит до здоровья и убила бы игрока, а
 * смертельная цена в настройке запрещена, это тоже нехватка. В творческом
 * режиме магия бесплатна, как и всё остальное.
 */
public final class RunePayment implements SpellPayment {

    @Override
    public Optional<SpellCaster.Failure> tryPay(ServerPlayer player, int runes, MagicConfig.Loaded config) {
        if (player.getAbilities().instabuild) {
            return Optional.empty();
        }
        MagicSettings.Cost cost = config.settings().cost();
        int crystals = cost.crystals(runes);
        Item crystal = config.crystalItem();
        if (crystals > 0 && countPayable(player.getInventory(), crystal) < crystals) {
            return Optional.of(SpellCaster.Failure.CRYSTAL);
        }

        double price = cost.foodPerRune() * runes;
        FoodData food = player.getFoodData();
        double fromSaturation = Math.min(food.getSaturationLevel(), price);
        double left = price - fromSaturation;
        double fromFood = Math.min(food.getFoodLevel(), left);
        double health = (left - fromFood) * cost.healthPerFood();
        if (health > 0 && !cost.canKill() && health >= player.getHealth()) {
            return Optional.of(SpellCaster.Failure.PRICE);
        }

        takeCrystals(player.getInventory(), crystal, crystals);
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
        return Optional.empty();
    }

    /** Сколько хрусталя можно отдать: основной инвентарь и вторая рука. Броня не в счёт. */
    private static int countPayable(Inventory inventory, Item crystal) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(crystal)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(crystal)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeCrystals(Inventory inventory, Item crystal, int amount) {
        int left = take(inventory.items, crystal, amount);
        take(inventory.offhand, crystal, left);
        inventory.setChanged();
    }

    /** @return сколько ещё осталось взять */
    private static int take(Iterable<ItemStack> stacks, Item crystal, int amount) {
        for (ItemStack stack : stacks) {
            if (amount <= 0) {
                break;
            }
            if (stack.is(crystal)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        return amount;
    }
}
