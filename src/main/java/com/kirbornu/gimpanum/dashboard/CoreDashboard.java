package com.kirbornu.gimpanum.dashboard;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.kirbornu.gimpanum.core.CoreBlock;
import com.kirbornu.gimpanum.core.CoreBlockEntity;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.kirbornu.gimpanum.core.CoreIndex;
import com.kirbornu.gimpanum.integration.FtbTeamsSupport;
import com.kirbornu.gimpanum.network.DashboardOpenPayload;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Серверная часть консоли Ядер.
 *
 * <p>Консоль — второе лицо у команд {@code /gimpanum}, а не отдельная
 * механика: всё, что она делает, можно сделать и командами. Разница в том, что
 * командой Ядра перебирают по одному и по имени, а консоль показывает их все
 * разом — включая те, что стоят на кораблях в выгруженных чанках, — и умеет
 * применить одно решение сразу ко многим.
 *
 * <p>Права проверяются здесь, а не только при открытии окна. Открытое окно —
 * не пропуск: клиент вправе прислать любой пакет когда угодно, и каждый из них
 * обязан спросить права заново.
 */
public final class CoreDashboard {

    /** Тот же порог, что и на команды и на настройку Ядра правой кнопкой. */
    public static final int PERMISSION = CoreBlock.REQUIRED_PERMISSION_LEVEL;

    /**
     * Список команд Ядра выполняется с правами четвёртого уровня, поэтому
     * править его вправе только тот, у кого они уже есть. Иначе консоль стала
     * бы способом выдать себе что угодно.
     */
    public static final int COMMAND_PERMISSION = 4;

    private CoreDashboard() {
    }

    // --- Отправка состояния ---------------------------------------------------

    public static void send(ServerPlayer player) {
        if (!player.hasPermissions(PERMISSION)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new DashboardOpenPayload(rows(player.server), player.hasPermissions(COMMAND_PERMISSION)));
    }

    /**
     * Список строится по указателю, а не обходом мира.
     *
     * <p>Иначе открытие консоли означало бы чтение всех чанков со всеми
     * кораблями — по одному на каждое Ядро. Настройка берётся из снимка в
     * указателе; у загруженного Ядра снимок совпадает с блоком, у выгруженного
     * он и есть единственное, что о нём известно.
     */
    private static List<CoreRow> rows(MinecraftServer server) {
        List<CoreRow> rows = new ArrayList<>();
        for (CoreIndex.Snapshot snapshot : CoreIndex.all(server)) {
            rows.add(new CoreRow(
                    snapshot.id(),
                    snapshot.name(),
                    snapshot.dimension().location(),
                    snapshot.pos(),
                    CoreIndex.isLoaded(server, snapshot.id()),
                    CoreLocks.isLocked(server, snapshot.id()),
                    snapshot.config()));
        }
        return rows;
    }

    // --- Действия -------------------------------------------------------------

    /** Выполняет действие над каждым из указанных Ядер и отчитывается о числе. */
    public static void act(ServerPlayer player, List<UUID> cores, CoreAction action, String argument) {
        if (!player.hasPermissions(PERMISSION)) {
            return;
        }
        MinecraftServer server = player.server;
        int done = 0;
        int blocked = 0;

        for (UUID coreId : cores) {
            if (action.mutating() && CoreLocks.isLocked(server, coreId)) {
                blocked++;
                continue;
            }
            if (perform(player, server, coreId, action, argument)) {
                done++;
            }
        }

        report(player, action, done, blocked);
        send(player);
    }

    private static boolean perform(ServerPlayer player, MinecraftServer server, UUID coreId,
                                   CoreAction action, String argument) {
        switch (action) {
            case LOCK -> {
                CoreLocks.setLocked(server, coreId, true);
                return true;
            }
            case UNLOCK -> {
                CoreLocks.setLocked(server, coreId, false);
                return true;
            }
            case DELETE, DETONATE -> {
                boolean removed = CoreIndex.delete(server, coreId, action == CoreAction.DETONATE);
                if (removed) {
                    // Замок исчезнувшего Ядра держать незачем, а идентификаторы
                    // не переиспользуются — совпадения с будущим Ядром не будет.
                    CoreLocks.forget(server, coreId);
                }
                return removed;
            }
            case TELEPORT -> {
                return teleport(player, server, coreId);
            }
            default -> {
                Optional<CoreBlockEntity> found = CoreIndex.findById(server, coreId, true);
                if (found.isEmpty()) {
                    return false;
                }
                return edit(found.get(), action, argument);
            }
        }
    }

    private static boolean edit(CoreBlockEntity core, CoreAction action, String argument) {
        CoreConfig config = core.config();
        switch (action) {
            case ARM -> core.setConfig(config.withArmed(true));
            case DISARM -> core.setConfig(config.withArmed(false));
            case TEAM_ADD -> {
                // Состав снимается сейчас и дальше не меняется — то же правило,
                // что и у команды привязки; см. BoundTeam.
                Optional<BoundTeam> team = FtbTeamsSupport.snapshot(core.getLevel().getServer(), argument);
                if (team.isEmpty()) {
                    return false;
                }
                List<BoundTeam> teams = new ArrayList<>(config.boundTeams());
                teams.removeIf(bound -> bound.teamName().equalsIgnoreCase(team.get().teamName()));
                teams.add(team.get());
                core.setConfig(config.withBoundTeams(teams));
            }
            case TEAM_REMOVE -> {
                List<BoundTeam> teams = new ArrayList<>(config.boundTeams());
                if (!teams.removeIf(bound -> bound.teamName().equalsIgnoreCase(argument))) {
                    return false;
                }
                core.setConfig(config.withBoundTeams(teams));
            }
            case TEAM_CLEAR -> core.setConfig(config.withBoundTeams(List.of()));
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Переносит игрока к Ядру.
     *
     * <p>Мировая позиция, а не {@code BlockPos}: у Ядра на физической
     * конструкции координаты блока указывают в служебный регион карты за
     * миллионы блоков, и телепорт туда высадил бы игрока в пустоте рядом с
     * невидимой копией корабля.
     */
    private static boolean teleport(ServerPlayer player, MinecraftServer server, UUID coreId) {
        Optional<CoreBlockEntity> found = CoreIndex.findById(server, coreId, true);
        if (found.isEmpty() || !(found.get().getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 where = SubLevelSupport.worldCenter(level, found.get().getBlockPos());
        player.teleportTo(level, where.x, where.y + 1.0, where.z, player.getYRot(), player.getXRot());
        return true;
    }

    // --- Правка настройки -----------------------------------------------------

    /**
     * Переносит выбранные части образца в указанные Ядра.
     *
     * <p>Имя переносится только когда получатель один и имя свободно: имена
     * обязаны оставаться уникальными, а «переименовать десяток Ядер одинаково»
     * — прямая порча указателя.
     */
    public static void apply(ServerPlayer player, List<UUID> cores, CoreConfig source, int sections) {
        if (!player.hasPermissions(PERMISSION)) {
            return;
        }
        MinecraftServer server = player.server;
        int mask = sections;

        if (!player.hasPermissions(COMMAND_PERMISSION)) {
            mask &= ~ConfigSection.COMMANDS.bit();
        }
        if (cores.size() != 1) {
            mask &= ~ConfigSection.NAME.bit();
        }

        int done = 0;
        int blocked = 0;
        for (UUID coreId : cores) {
            if (CoreLocks.isLocked(server, coreId)) {
                blocked++;
                continue;
            }
            Optional<CoreBlockEntity> found = CoreIndex.findById(server, coreId, true);
            if (found.isEmpty()) {
                continue;
            }
            CoreBlockEntity core = found.get();
            int effective = mask;
            if (ConfigSection.NAME.in(effective)
                    && CoreIndex.isNameTaken(server, source.name(), coreId)) {
                effective &= ~ConfigSection.NAME.bit();
                player.sendSystemMessage(Component.translatable("gimpanum.command.name_taken")
                        .withStyle(ChatFormatting.RED));
            }
            core.setConfig(ConfigSection.merge(core.config(), source, effective));
            done++;
        }

        int changed = done;
        player.sendSystemMessage(Component.translatable("gimpanum.dashboard.applied", changed)
                .withStyle(ChatFormatting.GRAY));
        if (blocked > 0) {
            warnBlocked(player, blocked);
        }
        send(player);
    }

    // --- Отчёты ---------------------------------------------------------------

    private static void report(ServerPlayer player, CoreAction action, int done, int blocked) {
        String key = switch (action) {
            case ARM -> "gimpanum.command.armed";
            case DISARM -> "gimpanum.command.disarmed";
            case LOCK -> "gimpanum.dashboard.locked";
            case UNLOCK -> "gimpanum.dashboard.unlocked";
            case DELETE -> "gimpanum.command.deleted";
            case DETONATE -> "gimpanum.command.detonated";
            case TELEPORT -> "gimpanum.dashboard.teleported";
            case TEAM_ADD -> "gimpanum.dashboard.team_added";
            case TEAM_REMOVE, TEAM_CLEAR -> "gimpanum.dashboard.team_removed";
        };
        player.sendSystemMessage(Component.translatable(key, done).withStyle(ChatFormatting.GRAY));
        if (blocked > 0) {
            warnBlocked(player, blocked);
        }
    }

    private static void warnBlocked(ServerPlayer player, int blocked) {
        player.sendSystemMessage(Component.translatable("gimpanum.dashboard.blocked", blocked)
                .withStyle(ChatFormatting.YELLOW));
    }
}
