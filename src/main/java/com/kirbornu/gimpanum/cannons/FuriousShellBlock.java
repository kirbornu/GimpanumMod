package com.kirbornu.gimpanum.cannons;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.DirectionalBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;

/**
 * Яростный снаряд в виде блока — в таком виде его и заряжают в пушку.
 *
 * <p>Снаряды Create Big Cannons живут блоками: их досылают в ствол, они стоят
 * там до выстрела и лишь в полёте становятся сущностью. Поэтому и у нашего
 * два обличья, и связывает их {@link #getAssociatedEntityType()}.
 *
 * <p>Взрыватель донный, как у бронебойного: такой срабатывает после
 * пробития, а не о первую же преграду.
 */
public class FuriousShellBlock extends SimpleShellBlock<FuriousShellProjectile> {

    public static final MapCodec<FuriousShellBlock> CODEC = simpleCodec(FuriousShellBlock::new);

    public FuriousShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean isBaseFuze() {
        return true;
    }

    @Override
    public EntityType<? extends FuriousShellProjectile> getAssociatedEntityType() {
        return BigCannons.FURIOUS_SHELL_PROJECTILE.get();
    }
}
