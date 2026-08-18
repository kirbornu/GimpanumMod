package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.recipe.ThawedOrganics;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /gimpanum thawing} — что лежит в Замороженной органике и перечитать список. */
public final class ThawingCommand {

    private ThawingCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("thawing")
                .then(Commands.literal("reload").executes(context -> {
                    ThawedOrganics.load(context.getSource().getServer());
                    return report(context.getSource(), true);
                }))
                .executes(context -> report(context.getSource(), false)));
    }

    private static int report(CommandSourceStack source, boolean broadcast) {
        int count = ThawedOrganics.count();
        source.sendSuccess(() -> Component.translatable("gimpanum.command.thawing_count",
                count, ThawedOrganics.path().toString()), broadcast);
        return count;
    }
}
