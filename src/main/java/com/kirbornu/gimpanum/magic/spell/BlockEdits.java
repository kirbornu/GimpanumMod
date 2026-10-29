package com.kirbornu.gimpanum.magic.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Изменение блоков заклинанием — с оглядкой на защиту территорий и пределы.
 *
 * <p>Своей защиты территорий в моде нет, а какая стоит в сборке, мод не знает.
 * Поэтому заклинание спрашивает так же, как спросила бы рука игрока: шлёт
 * событие установки блока от имени заклинателя. Если мод приватов в сборке
 * слушает это событие, на чужой земле он его отменит, и блок не встанет.
 * Слушает ли его именно тот мод, что стоит в сборке, не проверено: джарок
 * сборки в репозитории нет. Сверх того — ванильные запреты: защита спавна,
 * граница мира, режим приключения.
 */
public final class BlockEdits {

    private BlockEdits() {
    }

    public static boolean mayEdit(SpellSession session, BlockPos pos) {
        ServerLevel level = session.level();
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) {
            return false;
        }
        LivingEntity caster = session.caster();
        if (caster instanceof Player player && (!player.mayBuild() || !level.mayInteract(player, pos))) {
            return false;
        }
        return !EventHooks.onBlockPlace(caster, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }

    /**
     * Можно ли заклинанию сломать блок: те же запреты, что и на установку,
     * только событие — ломания, как от руки заклинателя.
     */
    public static boolean mayBreak(SpellSession session, BlockPos pos) {
        ServerLevel level = session.level();
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) {
            return false;
        }
        if (session.caster() instanceof ServerPlayer player) {
            if (!player.mayBuild() || !level.mayInteract(player, pos)) {
                return false;
            }
            return !CommonHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos,
                    level.getBlockState(pos)).isCanceled();
        }
        return true;
    }

    /**
     * Ставит блок, если можно, и записывает его как временный, если есть срок.
     *
     * @param ticks срок жизни в тиках; не больше нуля — навсегда
     * @return поставлен ли блок
     */
    public static boolean place(SpellSession session, BlockPos pos, BlockState state, BlockState restore, long ticks) {
        return place(session, pos, state, restore, ticks, false);
    }

    /** @param sameBlock временный блок свой, пока это тот же блок, в любом состоянии */
    public static boolean place(SpellSession session, BlockPos pos, BlockState state, BlockState restore, long ticks,
                                boolean sameBlock) {
        if (!mayEdit(session, pos) || !session.takeBlock()) {
            return false;
        }
        ServerLevel level = session.level();
        BlockState before = level.getBlockState(pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        SpellBlocks.noteCreated(level, pos, before, level.getBlockState(pos));
        if (ticks > 0) {
            TempBlocks.add(level, pos, level.getBlockState(pos), restore, ticks, sameBlock);
        }
        return true;
    }
}
