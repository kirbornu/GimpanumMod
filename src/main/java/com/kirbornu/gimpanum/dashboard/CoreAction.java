package com.kirbornu.gimpanum.dashboard;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Действие консоли, которое нельзя выразить правкой настройки.
 *
 * <p>Всё, что сводится к «поменять поле», едет отдельным пакетом с целой
 * настройкой и здесь не перечислено — иначе список разросся бы до полусотни
 * значений и повторил бы дерево команд. Сюда попадает то, что либо трогает
 * мир (удаление, телепорт), либо требует спросить чужой мод (привязка
 * экипажа FTB), либо вообще не является настройкой Ядра (замок консоли).
 */
public enum CoreAction {

    /** Снять предохранитель. */
    ARM(true),
    /** Поставить предохранитель. */
    DISARM(true),
    /** Запретить изменения через консоль. */
    LOCK(false),
    /** Снять запрет. Единственное действие, разрешённое над закрытым Ядром. */
    UNLOCK(false),
    /** Убрать Ядро из мира тихо. */
    DELETE(true),
    /** Убрать Ядро из мира со всеми посмертными последствиями. */
    DETONATE(true),
    /** Перенести игрока к Ядру. Мир не меняет, поэтому замок ему не помеха. */
    TELEPORT(false),
    /** Привязать экипаж FTB по имени: состав снимается на сервере. */
    TEAM_ADD(true),
    /** Отвязать экипаж по отображаемому имени. */
    TEAM_REMOVE(true),
    /** Отвязать все экипажи. */
    TEAM_CLEAR(true);

    private static final CoreAction[] VALUES = values();

    public static final StreamCodec<ByteBuf, CoreAction> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            index -> VALUES[Math.floorMod(index, VALUES.length)],
            CoreAction::ordinal);

    private final boolean mutating;

    CoreAction(boolean mutating) {
        this.mutating = mutating;
    }

    /** Меняет ли действие само Ядро — то, чему замок обязан помешать. */
    public boolean mutating() {
        return mutating;
    }
}
