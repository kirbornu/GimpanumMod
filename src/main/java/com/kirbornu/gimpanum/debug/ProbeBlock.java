package com.kirbornu.gimpanum.debug;

import com.kirbornu.gimpanum.Gimpanum;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Диагностический блок. В финальном моде его не будет — он существует, чтобы
 * выяснить на живом сервере, как Sable обходится с блоками на конструкциях.
 *
 * <p>Отвечает на два вопроса, от которых зависит вся логика Ядра:
 * <ol>
 *   <li>какая трансформация переводит координаты делянки в мировые;</li>
 *   <li>какие хуки разрушения вообще срабатывают, когда блок стоит на
 *       конструкции.</li>
 * </ol>
 *
 * <p>ПКМ пустой рукой — отчёт о положении. Shift+ПКМ — проверка эффектов:
 * маркеры в обеих координатах-кандидатах и безобидный взрыв в мировой.
 */
public class ProbeBlock extends Block {

    public ProbeBlock(BlockBehaviour.Properties properties) {
        super(properties);
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

        send(player, "Конструкция: " + info.name() + " [" + info.subLevelId() + "]", ChatFormatting.AQUA);
        send(player, "Делянка (чанк): " + info.plotPos(), ChatFormatting.GRAY);
        send(player, "Поза position:      " + fmt(info.posePosition()), ChatFormatting.GRAY);
        send(player, "Поза rotationPoint: " + fmt(info.poseRotationPoint()), ChatFormatting.GRAY);
        send(player, "Поза scale:         " + fmt(info.poseScale()), ChatFormatting.GRAY);

        // Ключевая проверка: игрок стоит вплотную к блоку, поэтому верный
        // кандидат обязан оказаться заметно ближе к нему, чем остальные.
        double distRaw = info.rawCenter().distanceTo(playerPos);
        double distWorld = info.worldCenter().distanceTo(playerPos);
        double distPlotRel = info.plotCenterCandidate().distanceTo(playerPos);

        send(player, "-- кандидаты (меньшая дистанция = верный) --", ChatFormatting.GOLD);
        send(player, String.format("сырой:        %s  -> %.2f", fmt(info.rawCenter()), distRaw),
                ChatFormatting.WHITE);
        send(player, String.format("transform:    %s  -> %.2f", fmt(info.worldCenter()), distWorld),
                pickColour(distWorld, distRaw, distPlotRel));
        send(player, String.format("отн. делянки: %s  -> %.2f", fmt(info.plotCenterCandidate()), distPlotRel),
                pickColour(distPlotRel, distRaw, distWorld));
        send(player, "Позиция игрока: " + fmt(playerPos), ChatFormatting.GRAY);

        Gimpanum.LOGGER.info("[зонд] raw={} world={} plotRel={} player={} distRaw={} distWorld={} distPlotRel={}",
                fmt(info.rawCenter()), fmt(info.worldCenter()), fmt(info.plotCenterCandidate()),
                fmt(playerPos), distRaw, distWorld, distPlotRel);
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
            dropMarker(level, maybeInfo.get().plotCenterCandidate(), Items.GOLD_BLOCK, "отн. делянки");
            send(player, "Маркеры: редстоун=сырой, изумруд=мировой, золото=отн.делянки",
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
        if (!state.is(newState.getBlock())) {
            logDestruction(level, pos, "onRemove", "поршень=" + movedByPiston);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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

    private static ChatFormatting pickColour(double self, double a, double b) {
        return (self <= a && self <= b) ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void send(Player player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }
}
