package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.dashboard.CoreDashboard;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /gimpanum dashboard} — открывает консоль Ядер тому, кто её вызвал. */
public final class DashboardCommand {

    private DashboardCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("dashboard").executes(context -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                context.getSource().sendFailure(
                        Component.translatable("gimpanum.dashboard.needs_player"));
                return 0;
            }
            CoreDashboard.send(player);
            return 1;
        }));
    }
}
