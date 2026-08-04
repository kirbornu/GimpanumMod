package com.kirbornu.gimpanum.integration;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
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
     * Команда, за которой закрепится захваченная Контрольная точка.
     *
     * <p>В отличие от {@link #crews(TeamManager)} личная команда игрока здесь
     * годится: захватить точку в одиночку — законный расклад, и оформлять её
     * тогда надо на самого захватчика.
     */
    static Optional<TeamOwner> ownerOf(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player)
                .map(team -> new TeamOwner(displayName(team), team.getOwner(),
                        team.getProperty(TeamProperties.COLOR).rgb()));
    }

    /**
     * Снимает состав экипажа на текущий момент.
     *
     * <p>Именно снимок, а не ссылка на команду: расклад боя должен быть
     * предсказуем и не меняться от того, кто вступил в команду после настройки
     * Ядра.
     */
    static Optional<BoundTeam> snapshot(MinecraftServer server, String query) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return findCrew(FTBTeamsAPI.api().getManager(), query)
                .map(team -> new BoundTeam(displayName(team), members(server, team)));
    }

    /** Отображаемые имена экипажей для подсказок. */
    static List<String> crewNames() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Team team : crews(FTBTeamsAPI.api().getManager())) {
            names.add(displayName(team));
        }
        return names;
    }

    /**
     * Экипажами считаем только party- и server-команды.
     *
     * <p>У каждого игрока в FTB Teams есть собственная личная «команда» из него
     * одного. В подсказках они выглядели как дубликаты ников и только мешали, а
     * привязать одного игрока и без того можно через {@code player add}.
     */
    private static List<Team> crews(TeamManager manager) {
        List<Team> crews = new ArrayList<>();
        for (Team team : manager.getTeams()) {
            if (team.isPartyTeam() || team.isServerTeam()) {
                crews.add(team);
            }
        }
        return crews;
    }

    /**
     * Ищет экипаж снисходительно.
     *
     * <p>{@code getTeamByName} у FTB Teams — точный поиск по короткому имени
     * вида {@code test#4add1687}, которое вручную не набрать. Поэтому сначала
     * пробуем отображаемое имя, а короткое остаётся запасным вариантом.
     */
    private static Optional<Team> findCrew(TeamManager manager, String query) {
        String needle = query.trim();
        List<Team> crews = crews(manager);

        for (Team team : crews) {
            if (displayName(team).equalsIgnoreCase(needle)) {
                return Optional.of(team);
            }
        }
        for (Team team : crews) {
            if (team.getShortName().equalsIgnoreCase(needle)) {
                return Optional.of(team);
            }
        }
        // Короткое имя без хеша: «test» найдёт «test#4add1687».
        for (Team team : crews) {
            String shortName = team.getShortName();
            int hash = shortName.indexOf('#');
            if (hash > 0 && shortName.substring(0, hash).equalsIgnoreCase(needle)) {
                return Optional.of(team);
            }
        }
        return Optional.empty();
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
