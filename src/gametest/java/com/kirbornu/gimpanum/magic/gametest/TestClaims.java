package com.kirbornu.gimpanum.magic.gametest;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Игрушечный «приват» для тестов: отменяет ломание и установку блоков в
 * отмеченных позициях — так же, как это делают моды защиты территорий через
 * события NeoForge.
 */
final class TestClaims {

    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

    static {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (PROTECTED.contains(event.getPos())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (PROTECTED.contains(event.getPos())) {
                event.setCanceled(true);
            }
        });
    }

    private TestClaims() {
    }

    /** Защитить абсолютную позицию. */
    static void protect(BlockPos absolute) {
        PROTECTED.add(absolute.immutable());
    }
}
