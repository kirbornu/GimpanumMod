package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.item.SealContents;
import com.kirbornu.gimpanum.item.SealItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Последствия подтверждённой гибели Ядра.
 *
 * <p>Сюда попадают только удаления, признанные настоящими: переезды при сборке
 * конструкции отсеивает {@link com.kirbornu.gimpanum.destruction.DestructionArbiter},
 * а Ядро на предохранителе не доходит и до него.
 *
 * <p>Всё происходит в мировых координатах точки гибели. У Ядра на физической
 * конструкции {@code BlockPos} указывает в служебный регион карты, поэтому
 * брать его напрямую нельзя — иначе взрыв и дроп уедут в невидимую игрокам
 * часть мира.
 */
public final class CoreDestruction {

    private CoreDestruction() {
    }

    /**
     * Порядок задан замыслом: сначала взрыв, потом команды, и только потом
     * Печать.
     *
     * <p>Взрыв идёт первым, чтобы привязанных игроков ещё застало на месте: к
     * моменту команд их может уже унести телепортом. Печать появляется
     * последней — так взрыв заведомо не может её задеть.
     */
    public static void run(ServerLevel level, Vec3 worldPos, CoreConfig config) {
        MinecraftServer server = level.getServer();
        SealContents contents = config.sealContents();

        if (config.explosionEnabled()) {
            explode(level, worldPos, config);
        }
        runCommands(server, level, worldPos, config, contents);

        if (config.sealEnabled()) {
            dropSeal(level, worldPos, contents);
        }
    }

    /**
     * Роняет Печать в мировых координатах: она остаётся на месте гибели Ядра, а
     * не уезжает вместе с обломками конструкции.
     */
    private static void dropSeal(ServerLevel level, Vec3 worldPos, SealContents contents) {
        SealDrops.spawnSeal(level, worldPos, contents);
    }

    /**
     * Выполняет настроенные команды для каждого затронутого игрока — как
     * привязанного лично, так и попавшего в привязанный экипаж.
     *
     * <p>Права уровня 4, как у консоли; {@code @s} — сам игрок; начало отсчёта
     * для {@code ~} — мировая позиция погибшего Ядра.
     */
    private static void runCommands(MinecraftServer server, ServerLevel level, Vec3 worldPos,
                                    CoreConfig config, SealContents contents) {
        if (config.commands().isEmpty()) {
            return;
        }

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

    /**
     * Взрыв идёт от постоянного фиктивного игрока, а не «ничей».
     *
     * <p>Безымянный взрыв моды на защиту территорий обрабатывают по-разному, и
     * предсказать это нельзя. С устойчивым источником достаточно один раз выдать
     * ему права в клейме — см. {@link CoreFakePlayer}.
     */
    private static void explode(ServerLevel level, Vec3 worldPos, CoreConfig config) {
        level.explode(CoreFakePlayer.get(level), worldPos.x, worldPos.y, worldPos.z,
                config.explosionPower(), config.explosionFire(),
                Level.ExplosionInteraction.BLOCK);
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
