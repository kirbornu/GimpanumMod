package com.kirbornu.gimpanum.worldgen;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.world.level.block.state.BlockState;

/** Что в Гимпануме считается природной породой, которую жилам можно замещать. */
public final class Terrain {

    private Terrain() {
    }

    /**
     * Космический песок и пепел — и ничего сверх.
     *
     * <p>Всё прочее в толще либо уже чья-то жила, либо постройка: астероид,
     * портал, хранилище. Жила, прошедшая насквозь, порезала бы их.
     */
    public static boolean rock(BlockState state) {
        return state.is(GimpanumContent.COSMIC_SAND.get()) || state.is(GimpanumContent.COSMIC_ASH.get());
    }
}
