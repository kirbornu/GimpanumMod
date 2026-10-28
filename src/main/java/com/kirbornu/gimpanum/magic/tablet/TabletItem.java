package com.kirbornu.gimpanum.magic.tablet;

import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Rune;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Рунная табличка: сетка ячеек под глифы, через неё кастуется заклинание.
 *
 * <p>Shift+ПКМ открывает сетку, ПКМ кастует. Содержимое хранится в самой
 * табличке ({@link TabletContents}), поэтому её можно передать другому игроку
 * вместе с заклинанием.
 */
public class TabletItem extends Item {

    public TabletItem(Properties properties) {
        super(properties);
    }

    public static TabletContents contents(ItemStack tablet) {
        return tablet.getOrDefault(MagicContent.TABLET_CONTENTS.get(), TabletContents.EMPTY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tablet = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(tablet);
        }
        if (player.isShiftKeyDown()) {
            open(server, hand, tablet);
        } else {
            // Катализатор — во второй руке, какая бы ни была у таблички.
            ItemStack catalyst = player.getItemInHand(
                    hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            SpellCaster.cast(server, contents(tablet).runescripts(), catalyst)
                    .ifPresent(failure -> player.displayClientMessage(SpellCaster.message(failure), true));
        }
        return InteractionResultHolder.success(tablet);
    }

    private static void open(ServerPlayer player, InteractionHand hand, ItemStack tablet) {
        // Предмет улучшения знает только сервер — клиенту он нужен, чтобы
        // слоты не принимали лишнего ещё до ответа сервера.
        Item upgrade = MagicConfig.ready() ? MagicConfig.get().upgradeItem() : null;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TabletMenu(id, inventory, hand, upgrade),
                tablet.getHoverName()), buffer -> {
            buffer.writeEnum(hand);
            buffer.writeUtf(upgrade == null ? "" : BuiltInRegistries.ITEM.getKey(upgrade).toString());
        });
    }

    @Override
    public void appendHoverText(ItemStack tablet, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TabletContents contents = contents(tablet);
        tooltip.add(Component.translatable("item.gimpanum.rune_tablet.size", contents.rows(), contents.columns())
                .withStyle(ChatFormatting.GRAY));
        List<int[]> words = contents.runescripts();
        if (words.isEmpty()) {
            tooltip.add(Component.translatable("item.gimpanum.rune_tablet.empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            String text = words.stream()
                    .map(word -> Arrays.stream(word).mapToObj(i -> Rune.byIndex(i).title()).collect(Collectors.joining("·")))
                    .collect(Collectors.joining(" "));
            tooltip.add(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("item.gimpanum.rune_tablet.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
