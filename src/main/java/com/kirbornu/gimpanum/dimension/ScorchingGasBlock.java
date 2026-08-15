package com.kirbornu.gimpanum.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Раскалённый небула-газ — вкрапления в песке Гимпанума.
 *
 * <p>Жжёт наступившего, как магмовый блок, и тем же типом урона
 * {@code hot_floor}: значит от него спасают ровно те же средства, о которых
 * игрок уже знает — приседание, огнестойкость, Морозная поступь. Заводить
 * свой тип урона было бы обманом ожиданий.
 */
public class ScorchingGasBlock extends Block {

    public ScorchingGasBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        // isSteppingCarefully — это приседание: осторожный шаг по магме не жжёт.
        if (entity instanceof LivingEntity && !entity.isSteppingCarefully()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }
}
