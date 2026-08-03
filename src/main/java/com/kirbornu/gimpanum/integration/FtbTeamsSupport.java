package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.core.BoundTeam;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Optional;

/**
 * Точка входа к поддержке команд FTB Teams.
 *
 * <p>Мод необязателен, поэтому здесь нет ни одного его типа: всё уходит в
 * {@link FtbTeamsBridge}, который загружается лишь после успешной проверки
 * {@link #isAvailable()}.
 */
public final class FtbTeamsSupport {

    private static final String FTB_TEAMS_MOD_ID = "ftbteams";

    private static Boolean modLoaded;
    private static boolean bridgeFailed;

    private FtbTeamsSupport() {
    }

    public static boolean isAvailable() {
        if (modLoaded == null) {
            modLoaded = ModList.get().isLoaded(FTB_TEAMS_MOD_ID);
            Gimpanum.LOGGER.info("FTB Teams {} — привязка команд {}",
                    modLoaded ? "обнаружен" : "не найден",
                    modLoaded ? "доступна" : "отключена");
        }
        if (!modLoaded || bridgeFailed) {
            return false;
        }
        try {
            return FtbTeamsBridge.isManagerReady();
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** Снимок состава команды на текущий момент. */
    public static Optional<BoundTeam> snapshot(MinecraftServer server, String teamName) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            return FtbTeamsBridge.snapshot(server, teamName);
        } catch (Throwable t) {
            fail(t);
            return Optional.empty();
        }
    }

    /** Имена команд для подсказок в командах. */
    public static List<String> teamNames() {
        if (!isAvailable()) {
            return List.of();
        }
        try {
            return FtbTeamsBridge.teamNames();
        } catch (Throwable t) {
            fail(t);
            return List.of();
        }
    }

    private static void fail(Throwable t) {
        // Ломать игру из-за несовместимости с чужим модом нельзя.
        bridgeFailed = true;
        Gimpanum.LOGGER.error("Мост к FTB Teams отключён после ошибки; "
                + "привязка команд выключена до перезапуска", t);
    }
}
