package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.integration.CorpseBridge;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /gimpanum corpse} — что Талисман видит на месте трупа.
 *
 * <p>Существует ровно затем, чтобы не гадать. Талисман умеет отвечать только
 * «нечего возвращать», а причин у этого ответа с полдюжины: нет записи о
 * смерти, запись есть, но в другом измерении, труп не в том радиусе, запись
 * принадлежит другому игроку, разбор оборвался. Команда проходит те же шаги и
 * называет тот, на котором всё встало, ничего при этом не трогая.
 */
public final class CorpseCommand {

    private CorpseCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("corpse")
                .executes(ctx -> run(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int run(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        List<String> lines = CorpseBridge.diagnose(player);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }
}
