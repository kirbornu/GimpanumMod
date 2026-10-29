package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.config.JsonConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.lore.LoreBooks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;
import java.util.Optional;

/**
 * Добыча обитателей Гимпанума — из того же {@code mobs.json}, что и их числа.
 *
 * <p>Не таблицами добычи: те живут в датапаке и правятся только им, а всё
 * остальное про мобов владелец сервера правит в папке настроек и перечитывает
 * одной командой. Формат нарочно простой — предмет, шанс, сколько штук:
 * этого хватало всем прежним таблицам, и ничего сверх этого они не умели.
 *
 * <p>Книга лора — здесь же, со своим шансом у каждого моба. Таблицей она и
 * раньше не выдавалась: книга каждый раз разная и собирается в коде.
 *
 * <p>Вместо предмета можно указать тег предметов — {@code "#gimpanum:glyphs"}:
 * тогда выпадает случайный предмет из тега, каждый раз свой. Так одна запись
 * описывает «любой из двенадцати глифов», а не двенадцать записей с долями
 * шанса.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class MobLoot {

    private MobLoot() {
    }

    /**
     * Сверить списки добычи с реестром предметов.
     *
     * <p>Один раз, при чтении файла: опечатка в имени предмета должна попасть в
     * журнал сразу, а не молча съедать выпадение при каждой смерти.
     */
    public static void check() {
        for (String name : MobStats.sections()) {
            JsonConfig.Section stats = MobStats.of(name);
            if (!stats.has("drops")) {
                continue;
            }
            for (JsonElement entry : stats.list("drops")) {
                if (parse(entry).isEmpty()) {
                    Gimpanum.LOGGER.warn("{}.drops — непонятная запись {}, пропускаю", name, entry);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        String name = MobStats.key(dead.getType());
        // Правило doMobLoot ванильные таблицы соблюдают сами, а это событие
        // приходит и при выключенном — проверяем за них.
        if (name == null || !(dead.level() instanceof ServerLevel level)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        // Истлевший сирота не роняет ничего — ни добычи, ни того, что на нём.
        if (dead instanceof DuneWalker walker && walker.withered()) {
            event.setCanceled(true);
            return;
        }
        JsonConfig.Section stats = MobStats.of(name);
        RandomSource random = dead.getRandom();
        for (JsonElement entry : stats.list("drops")) {
            roll(entry, random).ifPresent(stack -> drop(event, dead, stack));
        }
        if (random.nextDouble() < stats.number("lore_chance")) {
            LoreBooks.roll(random).ifPresent(book -> drop(event, dead, book));
        }
    }

    private static void drop(LivingDropsEvent event, LivingEntity dead, ItemStack stack) {
        event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), stack));
    }

    /**
     * Одна запись списка: что выпадает, шанс, от скольки до скольки штук.
     *
     * @param items один предмет или все предметы тега — тогда выпадает случайный
     */
    private record Drop(List<Item> items, double chance, int min, int max) {
    }

    /** Выпала ли запись и сколько штук. */
    private static Optional<ItemStack> roll(JsonElement entry, RandomSource random) {
        return parse(entry)
                .filter(drop -> random.nextDouble() < drop.chance())
                .map(drop -> new ItemStack(drop.items().get(random.nextInt(drop.items().size())),
                        drop.max() > drop.min() ? drop.min() + random.nextInt(drop.max() - drop.min() + 1) : drop.min()))
                .filter(stack -> !stack.isEmpty());
    }

    /**
     * Запись, если она похожа на запись и предмет есть в игре. Тег без
     * единого предмета — такая же ошибка, как неизвестный предмет.
     */
    private static Optional<Drop> parse(JsonElement entry) {
        try {
            JsonObject json = entry.getAsJsonObject();
            String name = json.get("item").getAsString();
            double chance = json.has("chance") ? json.get("chance").getAsDouble() : 1.0;
            int min = json.has("min") ? json.get("min").getAsInt() : 1;
            int max = json.has("max") ? json.get("max").getAsInt() : min;
            List<Item> items;
            if (name.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(name.substring(1)));
                items = BuiltInRegistries.ITEM.getTag(tag)
                        .map(set -> set.stream().map(Holder::value).toList())
                        .orElse(List.of());
            } else {
                items = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(name)).map(List::of).orElse(List.of());
            }
            return items.isEmpty() ? Optional.empty() : Optional.of(new Drop(items, chance, min, max));
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }
}
