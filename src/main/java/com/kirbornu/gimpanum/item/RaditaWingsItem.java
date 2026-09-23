package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.config.JsonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Крылья Радитажа — элитры, доведённые до предела: летают без ракет.
 *
 * <p>В полёте прыжок — взмах. Он слабее ракеты и тратит голод, зато ракеты не
 * нужны вовсе. Всё прочее — как у элитр: изнашиваются в полёте, чинятся
 * мембраной фантома, брони не дают.
 *
 * <p>Взмах решает сервер: клиент только сообщает, что прыгнул в полёте
 * ({@link com.kirbornu.gimpanum.network.WingFlapPayload}). Так все числа
 * живут в настройке сервера, а не расходятся с тем, что видит клиент.
 * Новую скорость сервер шлёт обратно тем же пакетом, каким доходит отдача от
 * удара.
 */
public class RaditaWingsItem extends ElytraItem {

    public RaditaWingsItem(Properties properties) {
        super(properties);
    }

    /** Взмах, если он сейчас возможен. Молча ничего не делает, если нет. */
    public static void flap(ServerPlayer player) {
        ItemStack wings = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(wings.getItem() instanceof RaditaWingsItem) || !player.isFallFlying()
                || !wings.canElytraFly(player) || player.getCooldowns().isOnCooldown(wings.getItem())) {
            return;
        }
        JsonConfig.Section config = ItemConfig.of("radita_wings");
        if (!player.getAbilities().instabuild
                && player.getFoodData().getFoodLevel() < config.integer("flap_min_food")) {
            return;
        }

        Vec3 before = player.getDeltaMovement();
        Vec3 after = before.add(player.getLookAngle().scale(config.number("flap_boost")))
                .add(0.0, config.number("flap_lift"), 0.0);
        // Взмах не тормозит: пикирующий быстрее предела остаётся при своей
        // скорости, меняется только направление.
        double limit = Math.max(config.number("max_speed"), before.length());
        if (after.length() > limit) {
            after = after.normalize().scale(limit);
        }
        player.setDeltaMovement(after);
        player.hurtMarked = true;

        player.causeFoodExhaustion((float) config.number("flap_exhaustion"));
        wings.hurtAndBreak(config.integer("flap_durability"), player, EquipmentSlot.CHEST);
        player.getCooldowns().addCooldown(wings.getItem(), config.integer("flap_cooldown_ticks"));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6F, 1.6F);
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.8, player.getZ(),
                6, 0.4, 0.2, 0.4, 0.02);
    }
}
