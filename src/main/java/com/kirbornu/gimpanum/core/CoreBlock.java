package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.destruction.DestructionArbiter;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ядро фоносомики — цель для боёв на физических конструкциях.
 *
 * <p>Выдаётся только в креативе и никогда не выпадает предметом: обычному
 * игроку его можно лишь уничтожить. При подтверждённом уничтожении роняет
 * Печать, выполняет настроенные команды для привязанных игроков и взрывается —
 * но только если снят предохранитель.
 *
 * <p>По умолчанию Ядро предельно хрупкое: ломается мгновенно и не держит
 * никакого взрыва. Тег неразрушимости превращает его в подобие бедрока — это
 * же и способ спокойно строить корабль, не боясь снести цель случайным ударом.
 *
 * <p>Уничтожение подтверждается не сразу: сборка физической конструкции
 * удаляет блок из мира, и без арбитража Ядро срабатывало бы при каждой сборке
 * корабля. Подробности — в {@link DestructionArbiter}.
 */
public class CoreBlock extends Block implements EntityBlock {

    /** Настраивать Ядро может только оператор. */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    /**
     * Признак неразрушимости продублирован в состоянии блока: неподвижность для
     * поршней и скорость разрушения читаются там, где позиция блока недоступна.
     */
    public static final BooleanProperty INVULNERABLE = BooleanProperty.create("invulnerable");

    private static final float INVULNERABLE_RESISTANCE = 3_600_000.0F;

    public CoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // Совпадает со значением по умолчанию в CoreConfig, поэтому
        // свежепоставленному Ядру не приходится сразу переписывать состояние.
        registerDefaultState(getStateDefinition().any().setValue(INVULNERABLE, true));
    }

    /**
     * Средняя кнопка мыши забирает Ядро вместе с настройкой.
     *
     * <p>Ядер на боевой карте делается много, и настраивать каждое заново
     * вручную невозможно. Поставленная копия получит все привязки и теги, но
     * своё собственное имя — см. {@link CoreConfig#asTemplate()}.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (level.getBlockEntity(pos) instanceof CoreBlockEntity core) {
            stack.set(GimpanumContent.CORE_CONFIG.get(), core.config().asTemplate());
        }
        return stack;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INVULNERABLE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreBlockEntity(pos, state);
    }

    // --- Прочность -----------------------------------------------------------

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // 0 — не ломается вовсе; 1 — ломается мгновенно с одного удара.
        return state.getValue(INVULNERABLE) ? 0.0F : 1.0F;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos,
                                        Explosion explosion) {
        return state.getValue(INVULNERABLE) ? INVULNERABLE_RESISTANCE : 0.0F;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return state.getValue(INVULNERABLE) ? PushReaction.BLOCK : PushReaction.NORMAL;
    }

    // --- Настройка -----------------------------------------------------------

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

    /** Показывает оператору текущую настройку. */
    private void describeTo(Player player, CoreBlockEntity core, Level level, BlockPos pos) {
        CoreConfig config = core.config();

        player.sendSystemMessage(Component.translatable("gimpanum.core.header", config.name())
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(
                config.armed() ? "gimpanum.core.armed" : "gimpanum.core.safe")
                .withStyle(config.armed() ? ChatFormatting.RED : ChatFormatting.GREEN));

        if (config.boundPlayers().isEmpty() && config.boundTeams().isEmpty()) {
            player.sendSystemMessage(Component.translatable("gimpanum.core.no_players")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            for (String name : config.boundPlayers()) {
                player.sendSystemMessage(Component.literal(" • " + name)
                        .withStyle(ChatFormatting.AQUA));
            }
            for (BoundTeam team : config.boundTeams()) {
                player.sendSystemMessage(Component.literal(
                                " ⚑ " + team.teamName() + " (" + team.members().size() + ")")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        if (config.commands().isEmpty()) {
            player.sendSystemMessage(Component.translatable("gimpanum.core.no_commands")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            for (String command : config.commands()) {
                player.sendSystemMessage(Component.literal(" • " + command)
                        .withStyle(ChatFormatting.WHITE));
            }
        }

        config.sealPostfix().ifPresent(postfix -> player.sendSystemMessage(
                Component.translatable("gimpanum.core.postfix", postfix).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.translatable("gimpanum.core.seal", config.sealEnabled())
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("gimpanum.core.explosion",
                config.explosionEnabled(), config.explosionPower(), config.explosionFire())
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("gimpanum.core.invulnerable",
                config.invulnerable()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("gimpanum.core.autofragile",
                config.autoDisableInvulnerable()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("gimpanum.core.on_sublevel",
                SubLevelSupport.isOnSubLevel(level, pos)).withStyle(ChatFormatting.DARK_GRAY));
    }

    // --- Уничтожение ---------------------------------------------------------

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            queueDestruction(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Снимает всё нужное до того, как {@code super.onRemove} уничтожит
     * блок-сущность, и передаёт арбитру. Мировую позицию тоже надо взять
     * сейчас: конструкция сдвинется, и точку гибели уже не восстановить.
     */
    private void queueDestruction(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CoreBlockEntity core)) {
            return;
        }

        CoreConfig config = core.config();
        if (!config.armed()) {
            // Предохранитель на месте — Ядро ведёт себя как обычный блок.
            CoreIndex.remove(core.coreId());
            return;
        }

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
        CoreIndex.remove(removal.blockId());

        Vec3 worldPos = removal.worldPos();
        Gimpanum.LOGGER.info("Ядро '{}' ({}) уничтожено в {}",
                config.name(), removal.blockId(), worldPos);
        CoreDestruction.run(serverLevel, worldPos, config);
    }
}
