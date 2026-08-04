package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.SealContents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Выполнение настроенных команд Ядра.
 *
 * <p>Список команд у Ядра один, а поводов выполнить его два — гибель Ядра и
 * периодическая выдача. Поэтому исполнение вынесено сюда: иначе два места
 * задавали бы разные права и разную точку отсчёта для {@code ~}, и поведение
 * разъехалось бы незаметно.
 */
public final class CoreCommandRunner {

    private CoreCommandRunner() {
    }

    /**
     * Выполняет команды для каждого затронутого игрока — как привязанного лично,
     * так и попавшего в привязанный экипаж.
     *
     * <p>Права уровня 4, как у консоли; {@code @s} — сам игрок; начало отсчёта
     * для {@code ~} — мировая позиция Ядра. Именно мировая: у Ядра на физической
     * конструкции {@code BlockPos} указывает в служебный регион карты.
     */
    public static void run(ServerLevel level, Vec3 worldPos, CoreConfig config) {
        if (config.commands().isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        SealContents contents = config.sealContents();

        List<String> targets = contents.allPlayers();
        for (String name : targets) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player == null) {
                Gimpanum.LOGGER.info("Ядро '{}': игрок {} не в сети, команды для него пропущены",
                        config.name(), name);
                continue;
            }

            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withEntity(player)
                    .withPosition(worldPos)
                    .withPermission(4)
                    .withSuppressedOutput();

            for (String command : config.commands()) {
                // Одна кривая команда не должна отменять остальные и взрыв.
                try {
                    server.getCommands().performPrefixedCommand(source, stripSlash(command));
                } catch (Exception e) {
                    Gimpanum.LOGGER.error("Ядро '{}': команда '{}' для {} не выполнилась",
                            config.name(), command, name, e);
                }
            }
        }
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
