package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Единственный класс мода, который напрямую ссылается на типы FTB Teams.
 *
 * <p>Изоляция такая же, как у моста к Sable: JVM загружает класс лениво,
 * поэтому пока {@link FtbTeamsSupport} не вызовет отсюда метод, отсутствие FTB
 * Teams ничем не грозит. Не обращаться к этому классу, минуя
 * {@link FtbTeamsSupport}.
 */
final class FtbTeamsBridge {

    private FtbTeamsBridge() {
    }

    static boolean isManagerReady() {
        return FTBTeamsAPI.api().isManagerLoaded();
    }

    /**
     * Снимает состав команды на текущий момент.
     *
     * <p>Именно снимок, а не ссылка на команду: расклад боя должен быть
     * предсказуем и не меняться от того, кто вступил в команду после настройки
     * Ядра.
     */
    static Optional<BoundTeam> snapshot(MinecraftServer server, String teamName) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        return manager.getTeamByName(teamName)
                .map(team -> new BoundTeam(displayName(team), members(server, team)));
    }

    static List<String> teamNames() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            names.add(team.getShortName());
        }
        return names;
    }

    private static String displayName(Team team) {
        String shown = team.getName().getString();
        return shown.isBlank() ? team.getShortName() : shown;
    }

    /**
     * Состав команды хранится идентификаторами, а Печать показывает ники,
     * поэтому имена берём из кэша профилей сервера. Игрок, которого сервер
     * никогда не видел, останется без имени и будет пропущен.
     */
    private static List<String> members(MinecraftServer server, Team team) {
        List<String> names = new ArrayList<>();
        GameProfileCache cache = server.getProfileCache();

        for (UUID member : team.getMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                names.add(online.getGameProfile().getName());
            } else if (cache != null) {
                cache.get(member).map(GameProfile::getName).ifPresent(names::add);
            }
        }
        return names;
    }
}
