package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.converter.ConverterBlockEntity;
import com.kirbornu.gimpanum.converter.ConverterConfig;
import com.kirbornu.gimpanum.converter.ConverterIndex;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Настройка Фонос-конвертеров командами.
 *
 * <p>Конвертер адресуется координатами, а не именем: он неподвижен и стоит там,
 * куда его поставил оператор, поэтому координаты у него обычные и вводимые. Это
 * и отличает его от Ядра, которое уезжает на физическую конструкцию и получает
 * восьмизначные координаты в служебном регионе карты.
 */
public final class ConverterCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_A_CONVERTER =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.not_a_converter"));

    private ConverterCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root,
                                CommandBuildContext buildContext) {
        root.then(Commands.literal("converter")
                .then(Commands.literal("list").executes(ConverterCommand::list))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("show")
                                .executes(ConverterCommand::show))
                        .then(Commands.literal("input")
                                .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument.item(buildContext))
                                        .then(Commands.argument("quota", IntegerArgumentType.integer(1))
                                                .executes(ConverterCommand::setInput))))
                        .then(Commands.literal("output")
                                .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument.item(buildContext))
                                        .then(Commands.argument("count",
                                                        IntegerArgumentType.integer(1, ConverterConfig.MAX_OUTPUT_COUNT))
                                                .executes(ConverterCommand::setOutput))))
                        .then(Commands.literal("label")
                                .then(Commands.literal("clear")
                                        .executes(context -> setLabel(context, Optional.empty())))
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(context -> setLabel(context,
                                                Optional.of(StringArgumentType.getString(context, "text"))))))
                        .then(Commands.literal("reset")
                                .executes(ConverterCommand::reset))));
    }

    private static ConverterBlockEntity resolve(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos) instanceof ConverterBlockEntity converter)) {
            throw ERROR_NOT_A_CONVERTER.create();
        }
        return converter;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<ConverterIndex.Entry> entries = ConverterIndex.all(source.getServer());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("gimpanum.command.no_converters"), false);
            return 0;
        }
        for (ConverterIndex.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal(" • " + entry.dimension().location()
                    + " " + entry.pos().toShortString()), false);
        }
        return entries.size();
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConverterBlockEntity converter = resolve(context);
        // Через источник, а не через игрока: команду может выполнить и консоль.
        converter.describe(line -> context.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int setInput(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConverterBlockEntity converter = resolve(context);
        Item item = net.minecraft.commands.arguments.item.ItemArgument.getItem(context, "item").getItem();
        int quota = IntegerArgumentType.getInteger(context, "quota");
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        converter.setConfig(converter.config().withInput(Optional.of(id), quota));
        context.getSource().sendSuccess(() -> Component.translatable(
                "gimpanum.command.converter_input_set", item.getDescription(), quota), true);
        return 1;
    }

    private static int setOutput(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConverterBlockEntity converter = resolve(context);
        Item item = net.minecraft.commands.arguments.item.ItemArgument.getItem(context, "item").getItem();
        int count = IntegerArgumentType.getInteger(context, "count");
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

        converter.setConfig(converter.config().withOutput(Optional.of(id), count));
        context.getSource().sendSuccess(() -> Component.translatable(
                "gimpanum.command.converter_output_set", count, item.getDescription()), true);
        return 1;
    }

    private static int setLabel(CommandContext<CommandSourceStack> context, Optional<String> label)
            throws CommandSyntaxException {
        ConverterBlockEntity converter = resolve(context);
        converter.setConfig(converter.config().withLabel(label));
        context.getSource().sendSuccess(() -> Component.translatable(
                "gimpanum.command.converter_label_set", label.orElse("—")), true);
        return 1;
    }

    /**
     * Обнуляет копилку, не трогая курс обмена.
     *
     * <p>Нужно после смены принимаемого предмета: набранное относилось к
     * прежнему, и засчитывать его в новую квоту было бы подарком.
     */
    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConverterBlockEntity converter = resolve(context);
        converter.resetProgress();
        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.converter_reset"), true);
        return 1;
    }
}
