package com.kirbornu.gimpanum.capture;

import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Контрольная точка — захватываемая цель на глобальной карте.
 *
 * <p>Отдельный блок, а не тег Ядра, намеренно: у точки нет ни предохранителя,
 * ни взрыва, ни Печати, ни привязок — только владелец. Смешивать это с боевым
 * Ядром означало бы держать в одной настройке два несовместимых набора правил.
 *
 * <p>Точка неподвижна и неразрушима, как бедрок: её нельзя ни офизичить на
 * конструкцию, ни сломать в выживании. Неподвижность держится на двух
 * независимых признаках, и достаточно любого одного:
 * <ul>
 *   <li>нулевая прочность {@code -1} в свойствах блока — по ней сборку
 *       отсеивает {@code SimAssemblyContraption.movementAllowed} из Simulated,
 *       ровно как бедрок;</li>
 *   <li>тег {@code simulated:non_movable} — второе условие того же метода.</li>
 * </ul>
 *
 * <p>Захват — удар рукой, но обрабатывается он не здесь, а в
 * {@link CaptureEvents}: клик по чужому клейму отменяет защита территории, и до
 * методов блока управление уже не доходит.
 */
public class CapturePointBlock extends Block implements EntityBlock {

    /** Сбрасывать точку в нейтральную может только оператор. */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    public CapturePointBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapturePointBlockEntity(pos, state);
    }

    /** Тикает только на сервере: частицы рассылает он же, клиенту тик не нужен. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != GimpanumContent.CAPTURE_POINT_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CapturePointBlockEntity point) {
                point.serverTick();
            }
        };
    }

    /** Поршни и конструкции точку не сдвигают. */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // --- Снос ----------------------------------------------------------------

    /**
     * Снятая точка уносит с собой и клейм.
     *
     * <p>Иначе на карте остался бы вечный чужой квадрат, снять который можно
     * было бы только вручную через команды OPAC.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof CapturePointBlockEntity point) {
            point.onDestroyed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
