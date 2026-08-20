package com.kirbornu.gimpanum.cannons;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import rbasamoyai.createbigcannons.munitions.big_cannon.ap_shell.APShellProjectile;

/**
 * Летящий Яростный снаряд.
 *
 * <p>Кода тут нет и не должно быть: всё поведение бронебойно-фугасного
 * снаряда Create Big Cannons нас устраивает, отличаются только числа. А числа
 * снаряд берёт по своему типу сущности из датапака —
 * {@code data/gimpanum/munition_properties/projectiles/furious_shell.json}.
 * Поэтому достаточно быть отдельным типом.
 */
public class FuriousShellProjectile extends APShellProjectile {

    public FuriousShellProjectile(EntityType<? extends FuriousShellProjectile> type, Level level) {
        super(type, level);
    }
}
