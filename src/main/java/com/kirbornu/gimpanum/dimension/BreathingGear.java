package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Что считается «водолазным костюмом» в Гимпануме.
 *
 * <p>Комплект — шлем на голове и баллон на груди. Оба набора заданы тегами
 * предметов, а не списком в коде: сборка может добавить туда свои шлемы, не
 * пересобирая мод.
 *
 * <p>Воздух в баллоне мы читаем и тратим через компонент Create
 * {@code create:banktank_air} — по имени, без ссылки на классы Create, чтобы
 * мод собирался и работал без него. Опечатка в имени («bank», не «back») —
 * не наша, так это зарегистрировано в самом Create.
 */
public final class BreathingGear {

    public static final TagKey<Item> HELMETS = TagKey.create(Registries.ITEM, Gimpanum.id("breathing_helmet"));
    public static final TagKey<Item> TANKS = TagKey.create(Registries.ITEM, Gimpanum.id("breathing_tank"));

    private static final ResourceLocation BACKTANK_AIR =
            ResourceLocation.fromNamespaceAndPath("create", "banktank_air");

    /**
     * Раз в сколько тиков баллон теряет единицу воздуха.
     *
     * <p>Ровно как под водой у самого Create: медный баллон вмещает 900 единиц,
     * значит одной заправки хватает на пятнадцать минут в Гимпануме.
     */
    public static final int DRAIN_INTERVAL = 20;

    private static boolean componentResolved;
    private static DataComponentType<?> airComponent;

    private BreathingGear() {
    }

    /** Есть ли на существе исправный комплект: шлем, баллон и воздух в нём. */
    public static boolean sealed(LivingEntity entity) {
        if (!entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMETS)) {
            return false;
        }
        ItemStack tank = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!tank.is(TANKS)) {
            return false;
        }
        Integer air = air(tank);
        // null — баллон не от Create и запаса не хранит: считаем бездонным.
        return air == null || air > 0;
    }

    /** Тратит единицу воздуха. Вызывать не чаще раза в {@link #DRAIN_INTERVAL} тиков. */
    public static void drain(LivingEntity entity) {
        ItemStack tank = entity.getItemBySlot(EquipmentSlot.CHEST);
        Integer air = air(tank);
        if (air != null && air > 0) {
            set(tank, air - 1);
        }
    }

    private static Integer air(ItemStack stack) {
        DataComponentType<?> type = component();
        if (type == null) {
            return null;
        }
        Object value = stack.get(type);
        return value instanceof Integer number ? number : null;
    }

    @SuppressWarnings("unchecked")
    private static void set(ItemStack stack, int air) {
        DataComponentType<?> type = component();
        if (type != null) {
            stack.set((DataComponentType<Integer>) type, air);
        }
    }

    private static DataComponentType<?> component() {
        if (!componentResolved) {
            componentResolved = true;
            airComponent = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BACKTANK_AIR);
        }
        return airComponent;
    }
}
