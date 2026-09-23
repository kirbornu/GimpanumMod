package com.kirbornu.gimpanum.structure;

import com.kirbornu.gimpanum.config.JsonConfig;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/**
 * Сгусток фоноса — награда структур Гимпанума.
 *
 * <p>Коснёшься — он рассыпается диковинками и спускает ловушку своей
 * структуры: какой именно, говорит свойство {@code kind}. Сломать его нельзя,
 * только коснуться: иначе ловушку обходили бы киркой, а взрывом — газом или
 * метеоритом — снесло бы награду впустую.
 *
 * <p>Награда скромная, по нескольку диковинок: всё по-настоящему ценное в
 * Гимпануме — через конвертеры.
 */
public class PhonosClotBlock extends Block {

    public static final EnumProperty<ClotKind> KIND = EnumProperty.create("kind", ClotKind.class);

    private static final List<DeferredItem<Item>> CURIOS = List.of(
            GimpanumContent.DARKNESS_CRYSTAL, GimpanumContent.FIRE_BAR, GimpanumContent.JADE_NUT,
            GimpanumContent.PLANT_ANCESTOR, GimpanumContent.SPARKLE_STRING, GimpanumContent.STONE_ROD,
            GimpanumContent.TENDERNESS_STONE, GimpanumContent.TRANSPARENT_BALL);

    public PhonosClotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, ClotKind.FROZEN_MEMORY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer opener) {
            touch(server, pos, state.getValue(KIND), opener);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void touch(ServerLevel level, BlockPos pos, ClotKind kind, ServerPlayer player) {
        level.removeBlock(pos, false);
        // Звук растворения слышат датчики скалка — в городе Примо это и
        // будит крикунов.
        level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.3, 0.3, 0.3, 0.08);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);

        JsonConfig.Section config = StructureConfig.of(kind.id());
        if (kind == ClotKind.RADITA_GALLERY && level.random.nextDouble() < config.number("wraith_chance")) {
            Traps.releaseArtist(level, pos, player);
            return;
        }
        scatter(level, pos, config.between("curios", level.random));
        switch (kind) {
            case FROZEN_MEMORY -> Traps.witherMemory(level, pos, player);
            case GLASS_DREAMS -> Traps.shatterDream(level, pos, player);
            default -> {
            }
        }
    }

    /** Рассыпать столько-то диковинок, каждую — случайную из восьми. */
    private static void scatter(ServerLevel level, BlockPos pos, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Item curio = CURIOS.get(random.nextInt(CURIOS.size())).get();
            popResource(level, pos, new ItemStack(curio));
        }
    }
}
