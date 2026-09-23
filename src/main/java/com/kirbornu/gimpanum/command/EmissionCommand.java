package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.dimension.NebulaPortal;
import com.kirbornu.gimpanum.emission.EmissionKind;
import com.kirbornu.gimpanum.emission.Emissions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;

/**
 * Выбросы вручную — для проверок и для того, кто ведёт игру.
 *
 * <p>{@code start} прерывает идущий выброс и сдвигает часы: следующий
 * случится через обычный промежуток после этого.
 */
public final class EmissionCommand {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("gimpanum.command.emission_unknown", id));

    private static final SimpleCommandExceptionType ERROR_NO_DIMENSION =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.emission_no_dimension"));

    private EmissionCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("emission")
                .then(Commands.literal("start")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(EmissionKind.values()).map(EmissionKind::id), builder))
                                .executes(EmissionCommand::start)))
                .then(Commands.literal("stop").executes(EmissionCommand::stop))
                .then(Commands.literal("status").executes(EmissionCommand::status)));
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "kind");
        EmissionKind kind = EmissionKind.byId(id).orElseThrow(() -> ERROR_UNKNOWN.create(id));
        Emissions.start(gimpanum(context), kind);
        context.getSource().sendSuccess(() -> Component.translatable("gimpanum.command.emission_started", kind.title()), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Emissions.stop(gimpanum(context))) {
            context.getSource().sendFailure(Component.translatable("gimpanum.command.emission_none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("gimpanum.command.emission_stopped"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        EmissionKind current = Emissions.current();
        if (current != null) {
            source.sendSuccess(() -> Component.translatable("gimpanum.command.emission_running", current.title()), false);
            return 1;
        }
        int seconds = Math.max(0, Emissions.untilNext(source.getServer()) / 20);
        source.sendSuccess(() -> Component.translatable("gimpanum.command.emission_next",
                String.format("%d:%02d", seconds / 60, seconds % 60)), false);
        return 0;
    }

    private static ServerLevel gimpanum(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getServer().getLevel(NebulaPortal.GIMPANUM);
        if (level == null) {
            throw ERROR_NO_DIMENSION.create();
        }
        return level;
    }
}
