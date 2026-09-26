package com.kirbornu.gimpanum.client;

import com.kirbornu.gimpanum.network.ConverterMarkersPayload;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.HudSession;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.List;

/**
 * Единственный класс мода, который напрямую ссылается на типы Xaero's Minimap.
 *
 * <p>Изоляция та же, что у мостов к Sable и FTB Teams: JVM загружает
 * класс лениво, поэтому пока {@link ConverterMarkers} не вызовет отсюда метод,
 * отсутствие Xaero ничем не грозит. Не обращаться к этому классу, минуя
 * {@link ConverterMarkers}.
 *
 * <p><b>Публичного API у Xaero нет.</b> Всё это внутренние классы, и обновление
 * мода вполне может их переименовать. Поэтому вызывающая сторона обязана ловить
 * {@link Throwable} и уметь жить без меток.
 */
final class XaeroWaypointBridge {

    /**
     * Свой набор вейпоинтов: чужие метки игрока трогать нельзя, а свои надо
     * уметь стирать целиком, не разбираясь, что там появилось помимо нас.
     */
    private static final String SET_ID = "Gimpanum";

    private XaeroWaypointBridge() {
    }

    static void apply(List<ConverterMarkersPayload.Marker> markers) {
        HudSession hud = HudSession.getCurrentSession();
        if (hud == null) {
            return;
        }
        MinimapSession minimap = hud.getSession(BuiltInHudModules.MINIMAP);
        if (minimap == null) {
            return;
        }
        MinimapWorld world = minimap.getWorldManager().getCurrentWorld();
        if (world == null) {
            return;
        }

        WaypointSet set = world.getWaypointSet(SET_ID);
        if (set == null) {
            // Пустой набор не заводим: в мире, где конвертеров нет (или мода
            // на сервере нет вовсе), он остался бы в списке наборов Xaero.
            if (markers.isEmpty()) {
                return;
            }
            // addWaypointSet возвращает не добавленный набор, а прежний с тем же
            // именем (это Map.put) — для нового это null. Работаем с созданным.
            set = WaypointSet.Builder.begin().setName(SET_ID).build();
            world.addWaypointSet(set);
        }

        // Набор перекладывается целиком: так снятый конвертер исчезает с карты
        // сам, без отдельного учёта того, что именно изменилось.
        set.clear();
        for (ConverterMarkersPayload.Marker marker : markers) {
            set.add(toWaypoint(marker));
        }
    }

    private static Waypoint toWaypoint(ConverterMarkersPayload.Marker marker) {
        String name = marker.label().isBlank() ? "Конвертер" : marker.label();
        Waypoint waypoint = new Waypoint(
                marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(),
                name, initials(name), WaypointColor.PURPLE, WaypointPurpose.NORMAL);
        // Временные метки не попадают в файл вейпоинтов игрока. Иначе они
        // копились бы там навсегда и оставались после удаления мода.
        waypoint.setTemporary(true);
        return waypoint;
    }

    /** Значок на карте — одна-две буквы названия, как это делает сам Xaero. */
    private static String initials(String name) {
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "K" : trimmed.substring(0, Math.min(2, trimmed.length()));
    }
}
