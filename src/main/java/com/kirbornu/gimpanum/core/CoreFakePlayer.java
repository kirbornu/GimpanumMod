package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

/**
 * Постоянная личность, от имени которой Ядро производит взрыв.
 *
 * <p>Взрыв без источника моды на защиту территорий трактуют кто как: одни
 * пропускают, другие режут урон по блокам. С фиктивным игроком у взрыва
 * появляется устойчивый UUID, который достаточно один раз внести в доверенные
 * в каждом клейме — и дальше поведение одинаково везде и всегда, независимо от
 * того, кто и чем снёс Ядро.
 *
 * <p>UUID выведен из имени и потому неизменен между запусками и мирами. Менять
 * имя нельзя: вместе с ним изменится и UUID, а значит слетят все выданные
 * разрешения.
 */
public final class CoreFakePlayer {

    public static final String NAME = "Gimpanum";

    /** {@code UUID.nameUUIDFromBytes("Gimpanum")} — записан явно, чтобы его можно было прочесть. */
    public static final UUID UUID_VALUE = UUID.fromString("4562d1ea-71c3-3d8c-b237-ace92257edf6");

    private static final GameProfile PROFILE = new GameProfile(UUID_VALUE, NAME);

    private CoreFakePlayer() {
    }

    /**
     * Фиктивный игрок для этого мира.
     *
     * @return {@code null}, если создать не удалось; вызывающий должен уметь
     *         обойтись без источника, а не падать
     */
    public static net.minecraft.server.level.ServerPlayer get(ServerLevel level) {
        try {
            return FakePlayerFactory.get(level, PROFILE);
        } catch (Throwable t) {
            Gimpanum.LOGGER.error("Не удалось создать фиктивного игрока Ядра; "
                    + "взрыв пойдёт без источника", t);
            return null;
        }
    }
}
