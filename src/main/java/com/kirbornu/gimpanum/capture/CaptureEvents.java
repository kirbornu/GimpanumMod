package com.kirbornu.gimpanum.capture;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Клики по Контрольной точке.
 *
 * <p>Вся обработка вынесена из блока в события намеренно. Захваченная точка
 * стоит в клейме своей команды, и клеймовый мод отменяет чужие клики по чужой
 * территории — а значит {@code Block.attack} и {@code useWithoutItem} у точки
 * просто перестали бы вызываться, и отбить её стало бы невозможно. Здесь же
 * подписка идёт с {@code receiveCanceled = true} и наинизшим приоритетом:
 * защита территории отработала и запретила ломать блок, а мы всё равно узнаём
 * об ударе и засчитываем захват.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class CaptureEvents {

    private CaptureEvents() {
    }

    /** Удар по точке — захват её командой ударившего. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        // В креативе левый клик сносит блок мгновенно; объявлять при этом о
        // захвате было бы враньём.
        if (player.isCreative()) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof CapturePointBlockEntity point)) {
            return;
        }
        point.captureBy(player);
    }

    /**
     * ПКМ показывает владельца, а оператору вприсядку — сбрасывает точку в
     * ничью. Отдельной команды для сброса нет: точка адресуется только собой.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (!(blockEntity instanceof CapturePointBlockEntity point)) {
            return;
        }

        if (player.isShiftKeyDown() && player.hasPermissions(CapturePointBlock.REQUIRED_PERMISSION_LEVEL)) {
            point.releaseToNeutral();
            player.sendSystemMessage(Component.translatable("gimpanum.point.released")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(point.owner()
                    .map(owner -> Component.translatable("gimpanum.point.held_by", owner.teamName())
                            .withStyle(ChatFormatting.GOLD))
                    .orElseGet(() -> Component.translatable("gimpanum.point.neutral")
                            .withStyle(ChatFormatting.AQUA)));
            if (player.hasPermissions(CapturePointBlock.REQUIRED_PERMISSION_LEVEL)) {
                player.sendSystemMessage(Component.translatable("gimpanum.point.hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // Гасим клик, чтобы предмет из руки не поставился блоком поверх точки.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
