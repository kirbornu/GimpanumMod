package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.dimension.NebulaPortal;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Ручная установка портальной арки.
 *
 * <p>Структуры генерируются только в новых чанках, поэтому на уже разведанной
 * карте естественных порталов не появится. Эта команда — способ поставить вход
 * там, где нужно, не переигрывая мир.
 */
public final class PortalCommand {

    private static final SimpleCommandExceptionType ERROR_NO_TEMPLATE =
            new SimpleCommandExceptionType(Component.translatable("gimpanum.command.portal_no_template"));

    private PortalCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("portal")
                .then(Commands.literal("place")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(PortalCommand::place))));
    }

    private static int place(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();

        StructureTemplate template = level.getStructureManager()
                .get(Gimpanum.id("nebula_portal"))
                .orElseThrow(ERROR_NO_TEMPLATE::create);

        // Ставим так, чтобы центр арки пришёлся на указанную точку, а не её угол.
        net.minecraft.core.Vec3i size = template.getSize();
        BlockPos corner = pos.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        template.placeInWorld(level, corner, corner,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.getRandom(), 2);

        context.getSource().sendSuccess(
                () -> Component.translatable("gimpanum.command.portal_placed",
                        pos.toShortString()), true);
        return 1;
    }
}
