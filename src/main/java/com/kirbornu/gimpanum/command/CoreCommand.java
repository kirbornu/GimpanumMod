package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.core.CoreBlock;
import com.kirbornu.gimpanum.core.CoreBlockEntity;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройка Ядра командами.
 *
 * <p>Временная замена экрану конфигурирования: механика разрушения так
 * тестируется задолго до того, как появится интерфейс. Позже экран будет
 * править ту же {@link CoreConfig}.
 */
public final class CoreCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_A_CORE =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.not_a_core"));
    private static final SimpleCommandExceptionType ERROR_NO_SUCH_INDEX =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.no_such_index"));

    private CoreCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gimpanum")
                // Уровень 2 — тот же порог, что и на открытие настройки ПКМ.
                .requires(source -> source.hasPermission(CoreBlock.REQUIRED_PERMISSION_LEVEL))
                .then(Commands.literal("core")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.literal("show")
                                        .executes(CoreCommand::show))
                                .then(Commands.literal("player")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(CoreCommand::addPlayer)))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("name", StringArgumentType.word())
                                                        .executes(CoreCommand::removePlayer)))
                                        .then(Commands.literal("clear")
                                                .executes(CoreCommand::clearPlayers)))
                                .then(Commands.literal("command")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                        .executes(CoreCommand::addCommand)))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                        .executes(CoreCommand::removeCommand)))
                                        .then(Commands.literal("clear")
                                                .executes(CoreCommand::clearCommands)))
                                .then(Commands.literal("explosion")
                                        .then(Commands.argument("power", FloatArgumentType.floatArg(0.0F, 100.0F))
                                                .then(Commands.argument("fire", BoolArgumentType.bool())
                                                        .executes(CoreCommand::setExplosion)))))));
    }

    private static CoreBlockEntity core(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        Level level = context.getSource().getLevel();
        if (!(level.getBlockEntity(pos) instanceof CoreBlockEntity core)) {
            throw ERROR_NOT_A_CORE.create();
        }
        return core;
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreConfig config = core(context).config();
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("gimpanum.core.header"), false);
        if (config.boundPlayers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.core.no_players"), false);
        } else {
            for (String name : config.boundPlayers()) {
                source.sendSuccess(() -> Component.literal(" • " + name), false);
            }
        }
        if (config.commands().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.core.no_commands"), false);
        } else {
            List<String> commands = config.commands();
            for (int i = 0; i < commands.size(); i++) {
                String line = " " + (i + 1) + ". " + commands.get(i);
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        source.sendSuccess(() -> Component.translatable("gimpanum.core.explosion",
                config.explosionPower(), config.explosionFire()), false);
        return 1;
    }

    private static int addPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        String name = StringArgumentType.getString(context, "name");

        List<String> names = new ArrayList<>(core.config().boundPlayers());
        if (names.contains(name)) {
            context.getSource().sendFailure(
                    Component.translatable("gimpanum.command.player_already_bound", name));
            return 0;
        }
        names.add(name);
        core.setConfig(core.config().withBoundPlayers(names));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.player_added", name), true);
        return 1;
    }

    private static int removePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        String name = StringArgumentType.getString(context, "name");

        List<String> names = new ArrayList<>(core.config().boundPlayers());
        if (!names.remove(name)) {
            context.getSource().sendFailure(
                    Component.translatable("gimpanum.command.player_not_bound", name));
            return 0;
        }
        core.setConfig(core.config().withBoundPlayers(names));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.player_removed", name), true);
        return 1;
    }

    private static int clearPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        int removed = core.config().boundPlayers().size();
        core.setConfig(core.config().withBoundPlayers(List.of()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.players_cleared", removed), true);
        return removed;
    }

    private static int addCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        String command = StringArgumentType.getString(context, "command");

        List<String> commands = new ArrayList<>(core.config().commands());
        commands.add(command);
        core.setConfig(core.config().withCommands(commands));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.command_added", command), true);
        return 1;
    }

    private static int removeCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        int index = IntegerArgumentType.getInteger(context, "index");

        List<String> commands = new ArrayList<>(core.config().commands());
        if (index > commands.size()) {
            throw ERROR_NO_SUCH_INDEX.create();
        }
        String removed = commands.remove(index - 1);
        core.setConfig(core.config().withCommands(commands));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.command_removed", removed), true);
        return 1;
    }

    private static int clearCommands(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        int removed = core.config().commands().size();
        core.setConfig(core.config().withCommands(List.of()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.commands_cleared", removed), true);
        return removed;
    }

    private static int setExplosion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CoreBlockEntity core = core(context);
        float power = FloatArgumentType.getFloat(context, "power");
        boolean fire = BoolArgumentType.getBool(context, "fire");

        core.setConfig(core.config().withExplosion(power, fire));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.explosion_set", power, fire), true);
        return 1;
    }
}
