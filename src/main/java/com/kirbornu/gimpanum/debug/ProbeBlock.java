package com.kirbornu.gimpanum.debug;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.destruction.DestructionArbiter;
import com.kirbornu.gimpanum.sublevel.SubLevelInfo;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Диагностический блок. В финальном моде его не будет — он существует, чтобы
 * выяснить на живом сервере, как Sable обходится с блоками на конструкциях.
 *
 * <p>Что уже выяснено на живом сервере (2026-08-01):
 * <ul>
 *   <li>мировую позицию даёт {@code pose.transformPosition(rawCenter)};</li>
 *   <li>{@code onRemove} срабатывает и от кирки, и от взрыва, то есть покрывает
 *       оба нужных триггера;</li>
 *   <li>сущность, созданная в координатах делянки, едет вместе с конструкцией,
 *       а созданная в мировых — остаётся в открытом мире.</li>
 * </ul>
 *
 * <p>Открытый вопрос: срабатывает ли {@code onRemove} при сборке и разборке
 * конструкции. Если да — Ядро будет «взрываться» при каждой сборке корабля, и
 * перенос придётся отличать от разрушения.
 *
 * <p>ПКМ пустой рукой — отчёт о положении. Shift+ПКМ — проверка эффектов.
 */
public class ProbeBlock extends Block implements EntityBlock {

    public ProbeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProbeBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            runEffectProbe(level, pos, player);
        } else {
            reportPosition(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Печатает игроку всё, что мод знает о положении этого блока. */
    private void reportPosition(Level level, BlockPos pos, Player player) {
        Optional<SubLevelInfo> maybeInfo = SubLevelSupport.describe(level, pos);

        send(player, "=== зонд Gimpanum ===", ChatFormatting.GOLD);
        send(player, "Sable загружен: " + SubLevelSupport.isSableLoaded(), ChatFormatting.GRAY);
        send(player, "BlockPos (сырой): " + pos.toShortString(), ChatFormatting.GRAY);

        if (maybeInfo.isEmpty()) {
            send(player, "Блок НЕ на конструкции — обычный мир.", ChatFormatting.YELLOW);
            send(player, "Позиция игрока: " + fmt(player.position()), ChatFormatting.GRAY);
            return;
        }

        SubLevelInfo info = maybeInfo.get();
        Vec3 playerPos = player.position();
        double distWorld = info.worldCenter().distanceTo(playerPos);

        send(player, "Конструкция: " + info.displayName() + " [" + info.subLevelId() + "]",
                ChatFormatting.AQUA);
        send(player, "Делянка (чанк): " + info.plotPos(), ChatFormatting.GRAY);
        send(player, "Поза position:      " + fmt(info.posePosition()), ChatFormatting.GRAY);
        send(player, "Поза rotationPoint: " + fmt(info.poseRotationPoint()), ChatFormatting.GRAY);
        send(player, "Поза scale:         " + fmt(info.poseScale()), ChatFormatting.GRAY);
        send(player, "Сырой центр:  " + fmt(info.rawCenter()), ChatFormatting.GRAY);
        send(player, String.format("Мировой центр: %s  (до игрока %.2f)", fmt(info.worldCenter()), distWorld),
                ChatFormatting.GREEN);
        send(player, "Позиция игрока: " + fmt(playerPos), ChatFormatting.GRAY);

        Gimpanum.LOGGER.info("[зонд] raw={} world={} player={} distWorld={}",
                fmt(info.rawCenter()), fmt(info.worldCenter()), fmt(playerPos), distWorld);
    }

    /**
     * Роняет маркеры в каждой координате-кандидате и создаёт взрыв в мировой.
     * Взрыв намеренно безвредный для блоков — иначе он разнесёт испытуемую
     * конструкцию прямо посреди эксперимента.
     */
    private void runEffectProbe(Level level, BlockPos pos, Player player) {
        Optional<SubLevelInfo> maybeInfo = SubLevelSupport.describe(level, pos);
        Vec3 rawCenter = Vec3.atCenterOf(pos);
        Vec3 worldCenter = maybeInfo.map(SubLevelInfo::worldCenter).orElse(rawCenter);

        send(player, "=== проверка эффектов ===", ChatFormatting.GOLD);

        dropMarker(level, rawCenter, Items.REDSTONE_BLOCK, "сырой");
        if (maybeInfo.isPresent()) {
            dropMarker(level, worldCenter, Items.EMERALD_BLOCK, "мировой");
            // В момент броска обе точки визуально совпадают. Разница вылезает
            // только когда конструкция сдвинется: редстоун лежит внутри делянки
            // и поедет вместе с ней, изумруд останется в открытом мире.
            send(player, "Редстоун=на конструкции, изумруд=в мире. Сдвинь штуковину — разъедутся.",
                    ChatFormatting.GRAY);
        } else {
            send(player, "Не на конструкции — брошен только сырой маркер.", ChatFormatting.YELLOW);
        }

        // radius 2, без огня, без разрушения блоков.
        level.explode(null, worldCenter.x, worldCenter.y, worldCenter.z,
                2.0F, false, Level.ExplosionInteraction.NONE);
        send(player, "Взрыв создан в: " + fmt(worldCenter), ChatFormatting.GRAY);
    }

    private void dropMarker(Level level, Vec3 at, net.minecraft.world.item.Item item, String label) {
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        level.addFreshEntity(entity);
        Gimpanum.LOGGER.info("[зонд] маркер '{}' брошен в {}", label, fmt(at));
    }

    // --- Хуки разрушения. Задача — выяснить, какие из них вообще вызываются,
    // --- когда блок стоит на физической конструкции.

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        logDestruction(level, pos, "playerWillDestroy", "игрок=" + player.getGameProfile().getName());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            logDestruction(level, pos, "onRemove", "поршень=" + movedByPiston);
            // Данные снимаем до super: он удалит блок-сущность вместе с UUID,
            // а мировую позицию после сдвига конструкции уже не восстановить.
            queueDestruction(level, pos, movedByPiston);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Ставит удаление в очередь арбитра. Настоящим уничтожением оно будет
     * признано, только если блок с тем же UUID не объявится где-то ещё.
     */
    private void queueDestruction(Level level, BlockPos pos, boolean movedByPiston) {
        if (!(level.getBlockEntity(pos) instanceof ProbeBlockEntity probe)) {
            Gimpanum.LOGGER.warn("[зонд] удаление в {} без блок-сущности — пропущено",
                    pos.toShortString());
            return;
        }

        boolean onSubLevel = SubLevelSupport.isOnSubLevel(level, pos);
        DestructionArbiter.onRemoved(new DestructionArbiter.PendingRemoval(
                probe.blockId(),
                level.dimension(),
                pos.immutable(),
                SubLevelSupport.worldCenter(level, pos),
                onSubLevel,
                DestructionArbiter.currentTick(),
                ProbeBlock::onConfirmedDestroyed
        ));
    }

    /**
     * Сюда попадают только подтверждённые уничтожения. Зонд ничего не взрывает
     * — он лишь фиксирует вердикт, чтобы схему можно было проверить, не рискуя
     * испытуемой конструкцией.
     */
    private static void onConfirmedDestroyed(DestructionArbiter.PendingRemoval removal) {
        Gimpanum.LOGGER.info("[зонд] ВЕРДИКТ: уничтожен id={} наКонструкции={} мировой={}",
                removal.blockId(), removal.wasOnSubLevel(), fmt(removal.worldPos()));
    }

    private void logDestruction(Level level, BlockPos pos, String hook, String extra) {
        if (level.isClientSide) {
            return;
        }
        boolean onSubLevel = SubLevelSupport.isOnSubLevel(level, pos);
        Vec3 world = SubLevelSupport.worldCenter(level, pos);
        Gimpanum.LOGGER.info("[зонд] хук={} наКонструкции={} сырой={} мировой={} {}",
                hook, onSubLevel, pos.toShortString(), fmt(world), extra);
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void send(Player player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }
}
