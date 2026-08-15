package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * В Гимпануме нет воздуха.
 *
 * <p>Держимся ванильного механизма утопления, а не заводим свой урон: игрок
 * видит привычную полоску пузырей и понимает, сколько у него осталось, без
 * единого объяснения. Спасает только комплект из {@link BreathingGear}.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class VacuumEvents {

    private VacuumEvents() {
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        if (!NebulaPortal.GIMPANUM.equals(entity.level().dimension())) {
            return;
        }
        // Уже задыхается по своим причинам (голова в воде) — не вмешиваемся.
        if (!event.canBreathe() || entity.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER)) {
            return;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        if (BreathingGear.sealed(entity)) {
            if (entity.tickCount % BreathingGear.DRAIN_INTERVAL == 0) {
                BreathingGear.drain(entity);
            }
            return;
        }
        // Расход воздуха не трогаем: там уже учтена Подводное дыхание на шлеме.
        event.setCanBreathe(false);
    }

    /** В вакууме пузырьков не бывает — иначе задыхаться будет «под водой». */
    @SubscribeEvent
    public static void onDrown(LivingDrownEvent event) {
        if (NebulaPortal.GIMPANUM.equals(event.getEntity().level().dimension())) {
            event.setBubbleCount(0);
        }
    }

    /** Предупреждаем на входе, чтобы смерть не выглядела беспричинной. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!NebulaPortal.GIMPANUM.equals(event.getTo())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && !player.isCreative()
                && !player.isSpectator()
                && !BreathingGear.sealed(player)) {
            player.displayClientMessage(
                    Component.translatable("gimpanum.vacuum.warning").withStyle(ChatFormatting.AQUA),
                    true);
        }
    }
}
