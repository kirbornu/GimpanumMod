package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Compilation;
import com.kirbornu.gimpanum.magic.core.FieldSet;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.core.Rune;
import com.kirbornu.gimpanum.magic.core.Vector3;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /gimpanum magic} — настройки рунной магии.
 *
 * <ul>
 *     <li>{@code magic} — что прочитано;</li>
 *     <li>{@code magic reload} — перечитать три файла; при ошибках ничего не
 *     меняется, а ошибки выводятся в чат с путём до места;</li>
 *     <li>{@code magic compile <заклинание>} — разобрать заклинание без каста:
 *     точки, арканы, роли. Нужно, чтобы сверять числа с песочницей. Руны
 *     пишутся латинскими id через точку, рунописи — через пробел:
 *     {@code an.dar be.kol}.</li>
 *     <li>{@code magic compile_in <набор> <заклинание>} — то же в другом наборе полей.</li>
 * </ul>
 */
public final class MagicCommand {

    /** Больше строк ошибок чат не покажет осмысленно — остальные в журнале. */
    private static final int MAX_ERRORS_IN_CHAT = 15;

    private MagicCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("magic")
                .executes(context -> summary(context.getSource()))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("compile")
                        .then(Commands.argument("spell", StringArgumentType.greedyString())
                                .executes(context -> compile(context, null))))
                .then(Commands.literal("compile_in")
                        .then(Commands.argument("set", StringArgumentType.word())
                                .then(Commands.argument("spell", StringArgumentType.greedyString())
                                        .executes(context -> compile(context,
                                                StringArgumentType.getString(context, "set")))))));
    }

    private static int summary(CommandSourceStack source) {
        if (!MagicConfig.ready()) {
            source.sendFailure(Component.translatable("gimpanum.magic.command.not_ready"));
            return 0;
        }
        MagicConfig.Loaded config = MagicConfig.get();
        source.sendSuccess(() -> Component.translatable("gimpanum.magic.command.summary",
                config.arcanes().size(), config.fields().sets().size(), MagicConfig.dir().toString()), false);
        return config.arcanes().size();
    }

    /** Перечитывает настройки и пишет итог в чат; общая часть с {@code /gimpanum config reload}. */
    public static int reload(CommandSourceStack source) {
        List<String> errors = MagicConfig.load(source.getServer());
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.magic.command.reloaded"), true);
            return summary(source);
        }
        source.sendFailure(Component.translatable("gimpanum.magic.command.errors", errors.size()));
        for (String error : errors.subList(0, Math.min(errors.size(), MAX_ERRORS_IN_CHAT))) {
            source.sendFailure(Component.literal(" • " + error));
        }
        if (errors.size() > MAX_ERRORS_IN_CHAT) {
            source.sendFailure(Component.translatable("gimpanum.magic.command.more_errors",
                    errors.size() - MAX_ERRORS_IN_CHAT));
        }
        return 0;
    }

    private static int compile(CommandContext<CommandSourceStack> context, String setName) {
        CommandSourceStack source = context.getSource();
        if (!MagicConfig.ready()) {
            source.sendFailure(Component.translatable("gimpanum.magic.command.not_ready"));
            return 0;
        }
        List<int[]> words = new ArrayList<>();
        for (String word : StringArgumentType.getString(context, "spell").trim().split("\\s+")) {
            String[] parts = word.split("[.·]");
            int[] runes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                Rune rune = Rune.byId(parts[i].toLowerCase(Locale.ROOT));
                if (rune == null) {
                    source.sendFailure(Component.translatable("gimpanum.magic.command.no_rune", parts[i]));
                    return 0;
                }
                runes[i] = rune.index();
            }
            words.add(runes);
        }
        MagicConfig.Loaded config = MagicConfig.get();
        Compilation compilation;
        FieldSet fields;
        if (setName == null) {
            SpellCaster.Compiled compiled = SpellCaster.compile(words, ItemStack.EMPTY, config);
            compilation = compiled.compilation();
            fields = compiled.fields();
        } else {
            fields = config.fields().sets().get(setName);
            if (fields == null || fields.isEmpty()) {
                source.sendFailure(Component.translatable("gimpanum.magic.command.no_set", setName));
                return 0;
            }
            compilation = Compilation.of(words, config.settings().turtle(), fields,
                    id -> config.arcane(id).order());
        }
        FieldSet used = fields;
        source.sendSuccess(() -> Component.translatable("gimpanum.magic.command.compile_header", used.name())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < words.size(); i++) {
            Vector3 p = compilation.points().get(i);
            String arcane = compilation.arcanes().get(i);
            Role role = compilation.roles()[i];
            String text = Arrays.stream(words.get(i)).mapToObj(r -> Rune.byIndex(r).title())
                    .collect(Collectors.joining("·"));
            Component line = Component.literal(String.format(Locale.ROOT, "%s → (%.4f, %.4f, %.4f) → %s [%d] ",
                            text, p.x(), p.y(), p.z(), arcane, config.arcane(arcane).order()))
                    .append(Component.translatable("gimpanum.magic.role." + role.key()))
                    .append(compilation.collapsed()[i]
                            ? Component.translatable("gimpanum.magic.role.collapsed") : Component.empty());
            source.sendSuccess(() -> line, false);
        }
        return compilation.program().steps().size();
    }
}
