package com.kirbornu.gimpanum.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Плоскость перехода в портальной арке.
 *
 * <p>Сквозь неё можно пройти — она и переносит того, кто вошёл. Ломать и
 * ставить порталы нельзя по замыслу: прочность {@code -1} делает блок
 * неразрушимым в выживании и отсеивает его из сборки конструкций, а предмета
 * у него нет вовсе.
 */
public class NebulaPortalBlock extends Block implements EntityBlock {

    public NebulaPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * Блок-сущность нужна не ради данных, а ради того, чтобы портал сам
     * записался в указатель при первой загрузке чанка — иначе выходы взять
     * неоткуда.
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NebulaPortalBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && !entity.isOnPortalCooldown()
                && entity.canChangeDimensions(level, serverLevel)) {
            NebulaPortal.teleport(serverLevel, entity);
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
}
