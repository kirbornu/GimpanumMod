package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.kirbornu.gimpanum.core.CoreBlock;
import com.kirbornu.gimpanum.core.CoreBlockEntity;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.kirbornu.gimpanum.core.CoreIndex;
import com.kirbornu.gimpanum.integration.FtbTeamsSupport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Настройка Ядра командами.
 *
 * <p>Ядро адресуется условным именем — координаты блока на физической
 * конструкции достигают восьмизначных значений и для ручного ввода непригодны.
 * Имя выдаётся автоматически при первой загрузке ({@code core1}, {@code core2}
 * и далее) и переименовывается командой {@code name}. Ветка {@code at}
 * оставлена на случай, когда имя неизвестно.
 */
public final class CoreCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_A_CORE =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.not_a_core"));
    private static final SimpleCommandExceptionType ERROR_UNKNOWN_CORE =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.unknown_core"));
    private static final SimpleCommandExceptionType ERROR_NO_SUCH_INDEX =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.no_such_index"));
    private static final SimpleCommandExceptionType ERROR_NAME_TAKEN =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.name_taken"));
    private static final SimpleCommandExceptionType ERROR_NO_SUCH_TEAM =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.no_such_team"));

    private static final SuggestionProvider<CommandSourceStack> CORE_NAMES =
            (context, builder) -> SharedSuggestionProvider.suggest(CoreIndex.names(), builder);
    private static final SuggestionProvider<CommandSourceStack> TEAM_NAMES =
            (context, builder) -> SharedSuggestionProvider.suggest(FtbTeamsSupport.crewNames(), builder);

    /** Ники игроков в сети — привязывают обычно присутствующих. */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    context.getSource().getOnlinePlayerNames(), builder);

    /** Как добраться до Ядра: по имени или по координатам. */
    @FunctionalInterface
    private interface CoreResolver {
        CoreBlockEntity resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private CoreCommand() {
    }

    /**
     * Подсказывает уже привязанные ники — отвязывать имеет смысл только их.
     *
     * <p>Ядро на этот момент уже разобрано из аргументов, но подсказки строятся
     * и по недописанной команде, поэтому промах здесь нормален и просто даёт
     * пустой список.
     */
    private static SuggestionProvider<CommandSourceStack> boundPlayersOf(CoreResolver resolver) {
        return (context, builder) -> {
            List<String> names;
            try {
                names = resolver.resolve(context).config().boundPlayers();
            } catch (Exception e) {
                names = List.of();
            }
            return SharedSuggestionProvider.suggest(names, builder);
        };
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gimpanum")
                // Уровень 2 — тот же порог, что и на открытие настройки ПКМ.
                .requires(source -> source.hasPermission(CoreBlock.REQUIRED_PERMISSION_LEVEL));

        root.then(Commands.literal("list").executes(CoreCommand::list));

        root.then(subcommands(
                Commands.argument("core", StringArgumentType.word()).suggests(CORE_NAMES),
                CoreCommand::byName));

        root.then(Commands.literal("at").then(subcommands(
                Commands.argument("pos", BlockPosArgument.blockPos()),
                CoreCommand::byPosition)));

        dispatcher.register(root);
    }

    /**
     * Одно и то же дерево подкоманд навешивается на оба способа адресации,
     * поэтому строится функцией, а не дублируется.
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T subcommands(
            T parent, CoreResolver resolver) {
        return parent
                .then(Commands.literal("show")
                        .executes(context -> show(context, resolver)))
                .then(Commands.literal("name")
                        .then(Commands.argument("new_name", StringArgumentType.word())
                                .executes(context -> rename(context, resolver))))
                .then(Commands.literal("arm")
                        .executes(context -> setArmed(context, resolver, true)))
                .then(Commands.literal("disarm")
                        .executes(context -> setArmed(context, resolver, false)))
                .then(Commands.literal("invulnerable")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setInvulnerable(context, resolver))))
                .then(Commands.literal("autofragile")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setAutoFragile(context, resolver))))
                .then(Commands.literal("player")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(context -> addPlayer(context, resolver))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(boundPlayersOf(resolver))
                                        .executes(context -> removePlayer(context, resolver))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearPlayers(context, resolver))))
                // Имя экипажа берём жадно: короткие имена FTB содержат '#', а
                // отображаемые — пробелы, и обычный word() их не принимает.
                .then(Commands.literal("team")
                        .then(Commands.literal("add")
                                .then(Commands.argument("team", StringArgumentType.greedyString()).suggests(TEAM_NAMES)
                                        .executes(context -> addTeam(context, resolver))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.greedyString())
                                        .executes(context -> removeTeam(context, resolver))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearTeams(context, resolver))))
                .then(Commands.literal("command")
                        .then(Commands.literal("add")
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(context -> addCommand(context, resolver))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> removeCommand(context, resolver))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearCommands(context, resolver))))
                .then(Commands.literal("postfix")
                        .then(Commands.literal("set")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> setPostfix(context, resolver))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearPostfix(context, resolver))))
                .then(Commands.literal("seal")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(context -> setSealEnabled(context, resolver))))
                .then(Commands.literal("explosion")
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> setExplosionEnabled(context, resolver))))
                        .then(Commands.literal("power")
                                .then(Commands.argument("power", FloatArgumentType.floatArg(0.0F, 100.0F))
                                        .then(Commands.argument("fire", BoolArgumentType.bool())
                                                .executes(context -> setExplosion(context, resolver))))));
    }

    // --- Адресация -----------------------------------------------------------

    private static CoreBlockEntity byName(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "core");
        return CoreIndex.find(context.getSource().getServer(), name)
                .orElseThrow(ERROR_UNKNOWN_CORE::create);
    }

    private static CoreBlockEntity byPosition(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        Level level = context.getSource().getLevel();
        if (!(level.getBlockEntity(pos) instanceof CoreBlockEntity core)) {
            throw ERROR_NOT_A_CORE.create();
        }
        return core;
    }

    // --- Подкоманды ----------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> context) {
        List<String> names = CoreIndex.names();
        CommandSourceStack source = context.getSource();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.command.no_cores"), false);
            return 0;
        }
        for (String name : names) {
            source.sendSuccess(() -> Component.literal(" • " + name), false);
        }
        return names.size();
    }

    private static int show(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreConfig config = resolver.resolve(context).config();
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("gimpanum.core.header", config.name()), false);
        source.sendSuccess(() -> Component.translatable(
                config.armed() ? "gimpanum.core.armed" : "gimpanum.core.safe"), false);

        if (config.boundPlayers().isEmpty() && config.boundTeams().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.core.no_players"), false);
        } else {
            for (String name : config.boundPlayers()) {
                source.sendSuccess(() -> Component.literal(" • " + name), false);
            }
            for (BoundTeam team : config.boundTeams()) {
                source.sendSuccess(() -> Component.literal(
                        " ⚑ " + team.teamName() + ": " + String.join(", ", team.members())), false);
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

        config.sealPostfix().ifPresent(postfix -> source.sendSuccess(
                () -> Component.translatable("gimpanum.core.postfix", postfix), false));
        source.sendSuccess(() -> Component.translatable("gimpanum.core.seal",
                config.sealEnabled()), false);
        source.sendSuccess(() -> Component.translatable("gimpanum.core.explosion",
                config.explosionEnabled(), config.explosionPower(), config.explosionFire()), false);
        source.sendSuccess(() -> Component.translatable("gimpanum.core.invulnerable",
                config.invulnerable()), false);
        source.sendSuccess(() -> Component.translatable("gimpanum.core.autofragile",
                config.autoDisableInvulnerable()), false);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        String newName = StringArgumentType.getString(context, "new_name");

        if (CoreIndex.isNameTaken(newName, core.coreId())) {
            throw ERROR_NAME_TAKEN.create();
        }
        core.setConfig(core.config().withName(newName));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.renamed", newName), true);
        return 1;
    }

    private static int setArmed(CommandContext<CommandSourceStack> context, CoreResolver resolver,
                                boolean armed) throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        core.setConfig(core.config().withArmed(armed));
        context.getSource().sendSuccess(() -> Component.translatable(
                armed ? "gimpanum.command.armed" : "gimpanum.command.disarmed",
                core.config().name()), true);
        return 1;
    }

    private static int setInvulnerable(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        boolean value = BoolArgumentType.getBool(context, "value");
        core.setConfig(core.config().withInvulnerable(value));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.invulnerable_set", value), true);
        return 1;
    }

    private static int setAutoFragile(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        boolean value = BoolArgumentType.getBool(context, "value");
        core.setConfig(core.config().withAutoDisableInvulnerable(value));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.autofragile_set", value), true);
        return 1;
    }

    private static int addPlayer(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
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

    private static int removePlayer(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
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

    private static int clearPlayers(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        int removed = core.config().boundPlayers().size();
        core.setConfig(core.config().withBoundPlayers(List.of()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.players_cleared", removed), true);
        return removed;
    }

    private static int addTeam(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        String teamName = StringArgumentType.getString(context, "team");

        Optional<BoundTeam> snapshot =
                FtbTeamsSupport.snapshot(context.getSource().getServer(), teamName);
        if (snapshot.isEmpty()) {
            throw ERROR_NO_SUCH_TEAM.create();
        }
        BoundTeam team = snapshot.get();

        // Повторная привязка обновляет снимок состава — это единственный способ
        // подтянуть изменившийся экипаж.
        List<BoundTeam> teams = new ArrayList<>(core.config().boundTeams());
        teams.removeIf(existing -> existing.teamName().equalsIgnoreCase(team.teamName()));
        teams.add(team);
        core.setConfig(core.config().withBoundTeams(teams));

        context.getSource().sendSuccess(() -> Component.translatable(
                "gimpanum.command.team_added", team.teamName(), team.members().size()), true);
        return team.members().size();
    }

    private static int removeTeam(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        String teamName = StringArgumentType.getString(context, "team").trim();

        List<BoundTeam> teams = new ArrayList<>(core.config().boundTeams());
        if (!teams.removeIf(existing -> existing.teamName().equalsIgnoreCase(teamName))) {
            context.getSource().sendFailure(
                    Component.translatable("gimpanum.command.team_not_bound", teamName));
            return 0;
        }
        core.setConfig(core.config().withBoundTeams(teams));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.team_removed", teamName), true);
        return 1;
    }

    private static int clearTeams(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        int removed = core.config().boundTeams().size();
        core.setConfig(core.config().withBoundTeams(List.of()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.teams_cleared", removed), true);
        return removed;
    }

    private static int addCommand(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        String command = StringArgumentType.getString(context, "command");

        List<String> commands = new ArrayList<>(core.config().commands());
        commands.add(command);
        core.setConfig(core.config().withCommands(commands));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.command_added", command), true);
        return 1;
    }

    private static int removeCommand(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
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

    private static int clearCommands(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        int removed = core.config().commands().size();
        core.setConfig(core.config().withCommands(List.of()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.commands_cleared", removed), true);
        return removed;
    }

    private static int setPostfix(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        String text = StringArgumentType.getString(context, "text");
        core.setConfig(core.config().withSealPostfix(Optional.of(text)));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.postfix_set", text), true);
        return 1;
    }

    private static int clearPostfix(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        core.setConfig(core.config().withSealPostfix(Optional.empty()));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.postfix_cleared"), true);
        return 1;
    }

    private static int setSealEnabled(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        boolean value = BoolArgumentType.getBool(context, "value");
        core.setConfig(core.config().withSealEnabled(value));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.seal_enabled_set", value), true);
        return 1;
    }

    private static int setExplosionEnabled(CommandContext<CommandSourceStack> context,
                                           CoreResolver resolver) throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        boolean value = BoolArgumentType.getBool(context, "value");
        core.setConfig(core.config().withExplosionEnabled(value));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.explosion_enabled_set", value), true);
        return 1;
    }

    private static int setExplosion(CommandContext<CommandSourceStack> context, CoreResolver resolver)
            throws CommandSyntaxException {
        CoreBlockEntity core = resolver.resolve(context);
        float power = FloatArgumentType.getFloat(context, "power");
        boolean fire = BoolArgumentType.getBool(context, "fire");

        core.setConfig(core.config().withExplosion(power, fire));
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.explosion_set", power, fire), true);
        return 1;
    }
}
