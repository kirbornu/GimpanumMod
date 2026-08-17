package com.kirbornu.gimpanum.command;

import com.kirbornu.gimpanum.lore.LoreBooks;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** {@code /gimpanum lore} — перечитать папку с лором и посмотреть, что вышло. */
public final class LoreCommand {

    private LoreCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("lore")
                .then(Commands.literal("reload").executes(ctx -> {
                    LoreBooks.load();
                    return report(ctx.getSource());
                }))
                .then(Commands.literal("roll").executes(ctx -> {
                    // Проверка вслепую: получить случайную книгу, не убивая мобов.
                    // Из консоли класть некуда, поэтому книга падает на месте
                    // выполнения — так команда работает и без игрока.
                    CommandSourceStack source = ctx.getSource();
                    return LoreBooks.roll(source.getLevel().getRandom())
                            .map(book -> {
                                if (source.getEntity() instanceof Player player) {
                                    player.getInventory().placeItemBackInInventory(book);
                                } else {
                                    Vec3 where = source.getPosition();
                                    ItemEntity dropped = new ItemEntity(source.getLevel(),
                                            where.x, where.y, where.z, book);
                                    source.getLevel().addFreshEntity(dropped);
                                }
                                return 1;
                            })
                            .orElseGet(() -> report(source));
                }))
                .executes(ctx -> report(ctx.getSource())));
    }

    private static int report(CommandSourceStack source) {
        int count = LoreBooks.count();
        source.sendSuccess(() -> Component.translatable("gimpanum.command.lore_count",
                count, LoreBooks.path().toString()), false);
        return count;
    }
}
