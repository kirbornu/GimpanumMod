package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.destruction.DestructionArbiter;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ядро — цель для боёв на физических конструкциях.
 *
 * <p>Выдаётся только в креативе и никогда не выпадает предметом: обычному
 * игроку его можно лишь уничтожить. При подтверждённом уничтожении роняет
 * Печать, выполняет настроенные команды для привязанных игроков и взрывается.
 *
 * <p>Уничтожение подтверждается не сразу: сборка физической конструкции
 * удаляет блок из мира, и без арбитража Ядро срабатывало бы при каждой сборке
 * корабля. Подробности — в {@link DestructionArbiter}.
 */
public class CoreBlock extends Block implements EntityBlock {

    /** Настраивать Ядро может только оператор. */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    public CoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Проверка прав только на сервере: клиенту в этом вопросе верить нельзя.
        if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof CoreBlockEntity core)) {
            return InteractionResult.PASS;
        }

        describeTo(player, core, level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * Показывает оператору текущую настройку. Полноценный экран появится
     * позже — пока настройка правится командами.
     */
    private void describeTo(Player player, CoreBlockEntity core, Level level, BlockPos pos) {
        CoreConfig config = core.config();

        player.sendSystemMessage(Component.translatable("gimpanum.core.header")
                .withStyle(ChatFormatting.GOLD));

        if (config.boundPlayers().isEmpty()) {
            player.sendSystemMessage(Component.translatable("gimpanum.core.no_players")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.translatable("gimpanum.core.players")
                    .withStyle(ChatFormatting.GRAY));
            for (String name : config.boundPlayers()) {
                player.sendSystemMessage(Component.literal(" • " + name)
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        if (config.commands().isEmpty()) {
            player.sendSystemMessage(Component.translatable("gimpanum.core.no_commands")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.translatable("gimpanum.core.commands")
                    .withStyle(ChatFormatting.GRAY));
            for (String command : config.commands()) {
                player.sendSystemMessage(Component.literal(" • " + command)
                        .withStyle(ChatFormatting.WHITE));
            }
        }

        player.sendSystemMessage(Component.translatable("gimpanum.core.explosion",
                config.explosionPower(), config.explosionFire()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("gimpanum.core.on_sublevel",
                SubLevelSupport.isOnSubLevel(level, pos)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            queueDestruction(level, pos, movedByPiston);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Снимает всё нужное до того, как {@code super.onRemove} уничтожит
     * блок-сущность, и передаёт арбитру. Мировую позицию тоже надо взять
     * сейчас: конструкция сдвинется, и точку гибели уже не восстановить.
     */
    private void queueDestruction(Level level, BlockPos pos, boolean movedByPiston) {
        if (!(level.getBlockEntity(pos) instanceof CoreBlockEntity core)) {
            Gimpanum.LOGGER.warn("Ядро в {} удалено без блок-сущности — последствий не будет",
                    pos.toShortString());
            return;
        }

        CoreConfig config = core.config();
        DestructionArbiter.onRemoved(new DestructionArbiter.PendingRemoval(
                core.coreId(),
                level.dimension(),
                pos.immutable(),
                SubLevelSupport.worldCenter(level, pos),
                SubLevelSupport.isOnSubLevel(level, pos),
                DestructionArbiter.currentTick(),
                removal -> onConfirmedDestroyed(level, removal, config)
        ));
    }

    private static void onConfirmedDestroyed(Level level,
                                             DestructionArbiter.PendingRemoval removal,
                                             CoreConfig config) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 worldPos = removal.worldPos();
        Gimpanum.LOGGER.info("Ядро {} уничтожено в {}; привязано игроков: {}",
                removal.blockId(), worldPos, config.boundPlayers().size());
        CoreDestruction.run(serverLevel, worldPos, config);
    }
}
