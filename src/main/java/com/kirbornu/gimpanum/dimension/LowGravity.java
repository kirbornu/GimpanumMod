package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Пониженная тяжесть Гимпанума.
 *
 * <p>На треть слабее обычной: прыжок чуть выше, падение чуть мягче, но
 * походка узнаваемая и снаряжения не требует. Полный ноль пробовать не стали —
 * без джетпака измерение стало бы непроходимым, а с ним ничем другим бы и не
 * занимались.
 *
 * <p>Живым тяжесть меняется <b>атрибутом</b>, а не толчком каждый тик.
 * {@code LivingEntity.getDefaultGravity()} читает как раз
 * {@link Attributes#GRAVITY}, поэтому одного модификатора хватает, и все
 * производные вещи — высота прыжка, урон от падения, поведение в воде —
 * сходятся сами. Модификатор временный: он не пишется в сохранение, а значит
 * не может пережить выход из измерения и остаться на игроке навсегда.
 *
 * <p>У неживого — предметов, снарядов, вагонеток — атрибутов нет вовсе, и там
 * приходится возвращать часть отобранной скорости обратно после тика. Это
 * приближение: положение за текущий тик уже посчитано с полной тяжестью, и
 * поправка сказывается со следующего. На глаз разницы нет, зато не нужно
 * подменять физику каждому виду сущности отдельно.
 *
 * <p><b>Корабли считает не этот класс.</b> Их ведёт Sable по своему датапаку;
 * то же число лежит в {@code data/gimpanum/dimension_physics/gimpanum.json}
 * полем {@code base_gravity}, и менять его нужно вместе с {@link #FACTOR} —
 * иначе корабль и стоящий на нём игрок будут падать по-разному.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class LowGravity {

    /** Доля от обычной тяжести. Ванильные 0.08 превращаются в 0.0533. */
    public static final double FACTOR = 2.0 / 3.0;

    private static final ResourceLocation MODIFIER_ID = Gimpanum.id("low_gravity");

    /**
     * Умножение на итог, а не прибавка: тогда правило переживает чужие
     * модификаторы тяжести — зелья, снаряжение, другие моды, — уменьшая то,
     * что получилось у них, а не подменяя собой.
     */
    private static final AttributeModifier MODIFIER = new AttributeModifier(
            MODIFIER_ID, FACTOR - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private LowGravity() {
    }

    /**
     * Появление в мире — единственный надёжный повод пересчитать модификатор.
     *
     * <p>Смена измерения пересоздаёт сущность и тоже проходит через это
     * событие, поэтому отдельно ловить переходы не нужно. Снятие здесь же:
     * сущность, попавшая в обычный мир, обязана потерять поблажку, даже если
     * временный модификатор почему-то пережил дорогу.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        if (inGimpanum(living)) {
            if (gravity.getModifier(MODIFIER_ID) == null) {
                gravity.addTransientModifier(MODIFIER);
            }
        } else {
            gravity.removeModifier(MODIFIER_ID);
        }
    }

    /** Неживому возвращаем часть отобранной тяжести обратно. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity || !inGimpanum(entity)) {
            return;
        }
        // Невесомым не помогаем: у них своя причина висеть в воздухе.
        // Стоящим и плывущим тоже: тяжесть к ним в этот тик не применялась.
        if (entity.isNoGravity() || entity.onGround() || entity.isInWater()) {
            return;
        }
        double give = entity.getGravity() * (1.0 - FACTOR);
        if (give > 0.0) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, give, 0.0));
        }
    }

    private static boolean inGimpanum(Entity entity) {
        return NebulaPortal.GIMPANUM.equals(entity.level().dimension());
    }
}
