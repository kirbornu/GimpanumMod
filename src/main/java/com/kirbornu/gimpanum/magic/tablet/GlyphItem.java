package com.kirbornu.gimpanum.magic.tablet;

import com.kirbornu.gimpanum.magic.core.Rune;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Глиф — камень с одной руной. При касте не расходуется: руны читаются с таблички, а не сжигаются. */
public class GlyphItem extends Item {

    private final Rune rune;

    public GlyphItem(Rune rune, Properties properties) {
        super(properties);
        this.rune = rune;
    }

    public Rune rune() {
        return rune;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gimpanum.glyph.rune", rune.title(), rune.index())
                .withStyle(ChatFormatting.GRAY));
    }
}
