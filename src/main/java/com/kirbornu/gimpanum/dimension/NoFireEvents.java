package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * В Гимпануме нечему гореть.
 *
 * <p>Огонь без воздуха — противоречие, которое видно с первого взгляда:
 * измерение, где игрок задыхается, не может при этом гореть.
 *
 * <p>Ловим не установку блока, а само использование зажигалки: огниво ставит
 * огонь напрямую, минуя событие установки блока, — поэтому запрет по блоку
 * ничего не давал. Список зажигалок вынесен в тег, чтобы сборка могла добавить
 * туда свои.
 *
 * <p>Молнии от разрядов Плазменной молнии закрыты в самом снаряде: он вызывает
 * показную молнию и наносит её урон сам.
 *
 * <p>Остальные источники огня — взрыв с огнём (так взрывается и Ядро по
 * умолчанию), раздатчик с огнивом, лава рядом с деревом, огненные шары,
 * распространение уже горящего — ставят огонь напрямую, без всякого события
 * установки. Зато любая такая установка оповещает соседей, и это оповещение
 * у NeoForge — событие. Им и ловим: огонь гаснет в тот же миг, как появился.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class NoFireEvents {

    public static final TagKey<Item> FIRE_STARTERS =
            TagKey.create(Registries.ITEM, Gimpanum.id("fire_starters"));

    private NoFireEvents() {
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!inGimpanum(event.getLevel()) || !event.getItemStack().is(FIRE_STARTERS)) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            player.displayClientMessage(
                    Component.translatable("gimpanum.fire.nothing_to_burn").withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    /** Запасная сеть: если огонь всё же придёт как обычная установка блока. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof BaseFireBlock && inGimpanum(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    /**
     * Огонь, поставленный в обход событий установки, гасится сразу.
     *
     * <p>Оповещение соседей приходит, когда блок уже стоит, поэтому огонь
     * просто убирается. Проверка блока идёт первой: событие приходит на
     * каждое изменение мира во всех измерениях, и дешёвое сравнение должно
     * отсеять почти всё.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof BaseFireBlock
                && event.getLevel() instanceof ServerLevel level
                && NebulaPortal.GIMPANUM.equals(level.dimension())) {
            level.removeBlock(event.getPos(), false);
        }
    }

    private static boolean inGimpanum(LevelAccessor accessor) {
        return accessor instanceof Level level && NebulaPortal.GIMPANUM.equals(level.dimension());
    }
}
